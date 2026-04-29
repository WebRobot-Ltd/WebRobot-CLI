package org.webrobot.cli.commands

import eu.webrobot.openapi.client.model.AgentDto
import org.webrobot.cli.manifest.{StageCatalog, YamlManifest, AtEnd}
import org.webrobot.cli.openapi.{JsonCliUtil, OpenApiHttp}
import picocli.CommandLine.{Command, Option => Opt}

import java.io.File
import scala.collection.JavaConverters._

// ─── helpers ─────────────────────────────────────────────────────────────────

private object WizardIO {
  def prompt(label: String, default: String = "")(implicit cmd: BaseSubCommand): String = {
    val hint = if (default.nonEmpty) s" [${cmd.ANSI_CYAN}$default${cmd.ANSI_RESET}]" else ""
    System.out.print(s"  $label$hint: ")
    System.out.flush()
    val line = scala.io.StdIn.readLine()
    if (line == null || line.trim.isEmpty) default else line.trim
  }

  def confirm(msg: String)(implicit cmd: BaseSubCommand): Boolean = {
    System.out.print(s"  $msg [Y/n]: ")
    System.out.flush()
    val line = scala.io.StdIn.readLine()
    line == null || line.trim.isEmpty || line.trim.equalsIgnoreCase("y")
  }

  def header(title: String)(implicit cmd: BaseSubCommand): Unit = {
    System.out.println(s"\n${cmd.ANSI_BOLD}$title${cmd.ANSI_RESET}")
    System.out.println("  " + "─" * (title.length + 2))
  }

  def showCommand(cmd2: BaseSubCommand, equivalent: String): Unit = {
    System.out.println(s"\n  ${cmd2.ANSI_BOLD}Comando equivalente:${cmd2.ANSI_RESET}")
    System.out.println(s"  ${cmd2.ANSI_CYAN}$equivalent${cmd2.ANSI_RESET}\n")
  }
}

// ─── agent wizard ─────────────────────────────────────────────────────────────

@Command(
  name = "agent",
  sortOptions = false,
  description = Array("Wizard interattivo per creare un nuovo agent.")
)
class AgentWizardCommand extends BaseSubCommand {

  override def startRun(): Unit = {
    this.init()
    implicit val self: BaseSubCommand = this

    WizardIO.header("Wizard — Nuovo Agent")

    // 1. Fetch e mostra categorie
    System.out.println(s"  ${ANSI_CYAN}Caricamento categorie...${ANSI_RESET}")
    val catsNode = OpenApiHttp.getJson(apiClient(), "/webrobot/api/categories")
    if (catsNode == null || !catsNode.isArray || catsNode.size() == 0) {
      System.out.println(s"  ${ANSI_RED}Nessuna categoria trovata.${ANSI_RESET}"); return
    }
    val cats = catsNode.elements().asScala.toList
    System.out.println()
    cats.zipWithIndex.foreach { case (c, i) =>
      val id   = Option(c.get("id")).map(_.asText("")).getOrElse("")
      val name = Option(c.get("name")).map(_.asText("")).getOrElse(id)
      System.out.println(s"  [${i + 1}] $name  ${ANSI_CYAN}($id)${ANSI_RESET}")
    }
    System.out.println()

    // 2. Selezione categoria
    val catInput = WizardIO.prompt("Categoria (numero o id)")
    val categoryId = try {
      val n = catInput.toInt
      if (n >= 1 && n <= cats.size) Option(cats(n - 1).get("id")).map(_.asText("")).getOrElse("") else catInput
    } catch { case _: NumberFormatException => catInput }
    if (categoryId.isEmpty) { System.out.println(s"  ${ANSI_RED}Categoria non valida.${ANSI_RESET}"); return }

    // 3. Nome e descrizione
    val agentName = WizardIO.prompt("Nome agent")
    if (agentName.isEmpty) { System.out.println(s"  ${ANSI_RED}Nome obbligatorio.${ANSI_RESET}"); return }

    val agentDesc = WizardIO.prompt("Descrizione")

    // 4. File codice (opzionale)
    val codeFile = WizardIO.prompt("File codice (lascia vuoto per saltare)")
    val codeContent: Option[String] = if (codeFile.nonEmpty) {
      val f = new File(codeFile)
      if (!f.exists()) {
        System.out.println(s"  ${ANSI_YELLOW}File non trovato, campo code lasciato vuoto.${ANSI_RESET}")
        None
      } else {
        Some(scala.io.Source.fromFile(f).getLines().mkString("\r\n"))
      }
    } else None

    // 5. Mostra comando equivalente e chiedi conferma
    val fileFlag = if (codeFile.nonEmpty) s" -f $codeFile" else ""
    val descFlag = if (agentDesc.nonEmpty) s""" -d "$agentDesc"""" else ""
    WizardIO.showCommand(this,
      s"""webrobot agent add -c $categoryId -n "$agentName"$descFlag$fileFlag""")

    if (!WizardIO.confirm("Creare l'agent?")) { System.out.println("  Annullato."); return }

    // 6. Esegui
    val dto = new AgentDto()
    dto.setName(agentName)
    dto.setDescription(agentDesc)
    dto.setCategoryId(categoryId)
    codeContent.foreach(dto.setCode)

    val result = OpenApiHttp.postJson(apiClient(), "/webrobot/api/agents", dto)
    val newId  = if (result != null) Option(result.get("id")).map(_.asText("")).getOrElse("") else ""

    System.out.println(s"\n  ${ANSI_GREEN}Agent creato!${ANSI_RESET}")
    if (newId.nonEmpty) System.out.println(s"  id: ${ANSI_BOLD}$newId${ANSI_RESET}")
    System.out.println(s"\n  Prossimi passi:")
    System.out.println(s"  ${ANSI_CYAN}webrobot agent get -c $categoryId -i $newId${ANSI_RESET}")
  }
}

// ─── pipeline wizard ──────────────────────────────────────────────────────────

@Command(
  name = "pipeline",
  sortOptions = false,
  description = Array("Wizard interattivo per creare ed eseguire una pipeline.")
)
class PipelineWizardCommand extends BaseSubCommand {

  @Opt(names = Array("-f", "--file"), description = Array("File YAML di output (default: pipeline.yaml)"))
  private var outputFile: String = "pipeline.yaml"

  @Opt(names = Array("-F", "--follow"), description = Array("Segui l'esecuzione dopo l'avvio"))
  private var follow: Boolean = false

  override def startRun(): Unit = {
    this.init()
    implicit val self: BaseSubCommand = this

    try { StageCatalog.fetchRemote(apiClient()) } catch { case _: Exception => }

    WizardIO.header("Wizard — Nuova Pipeline")

    // ── 1. Progetto ────────────────────────────────────────────────────────
    System.out.println(s"  ${ANSI_CYAN}Caricamento progetti...${ANSI_RESET}")
    val projNode = OpenApiHttp.getJson(apiClient(), "/webrobot/api/projects")
    val projects = if (projNode != null && projNode.isArray) projNode.elements().asScala.toList else List.empty
    if (projects.isEmpty) { System.out.println(s"  ${ANSI_RED}Nessun progetto trovato.${ANSI_RESET}"); return }
    System.out.println()
    projects.zipWithIndex.foreach { case (p, i) =>
      val id   = Option(p.get("id")).map(_.asText("")).getOrElse("")
      val name = Option(p.get("name")).map(_.asText("")).getOrElse(id)
      System.out.println(s"  [${i + 1}] ${ANSI_BOLD}$name${ANSI_RESET}  ${ANSI_CYAN}($id)${ANSI_RESET}")
    }
    System.out.println()
    val projInput  = WizardIO.prompt("Progetto (numero o id)")
    val projectRef = try {
      val n = projInput.toInt
      if (n >= 1 && n <= projects.size) Option(projects(n - 1).get("id")).map(_.asText("")).getOrElse("") else projInput
    } catch { case _: NumberFormatException => projInput }
    if (projectRef.isEmpty) { System.out.println(s"  ${ANSI_RED}Progetto non valido.${ANSI_RESET}"); return }

    // ── 2. Metadati e file ─────────────────────────────────────────────────
    System.out.println()
    val pipelineName = WizardIO.prompt("Nome pipeline")
    if (pipelineName.isEmpty) { System.out.println(s"  ${ANSI_RED}Nome obbligatorio.${ANSI_RESET}"); return }
    val pipelineDesc = WizardIO.prompt("Descrizione (opzionale)")
    val yamlPath     = WizardIO.prompt("File YAML di output", outputFile)
    val file         = new File(yamlPath)

    val (docs, pd) = YamlManifest.ensurePipeline(file)
    val meta = pd.getOrElseUpdate("metadata", scala.collection.mutable.Map[String, Any]())
                 .asInstanceOf[scala.collection.mutable.Map[String, Any]]
    meta("name") = pipelineName
    if (pipelineDesc.nonEmpty) meta("description") = pipelineDesc
    val spec = pd.getOrElseUpdate("spec", scala.collection.mutable.Map[String, Any]())
                 .asInstanceOf[scala.collection.mutable.Map[String, Any]]
    spec("project") = projectRef

    // ── 3. Input dataset ───────────────────────────────────────────────────
    val inputDs = WizardIO.prompt("Dataset di input (id o lascia vuoto per saltare)")
    if (inputDs.nonEmpty) YamlManifest.setInput(pd, Some(inputDs), None)

    // ── 4. Stage loop guidato ──────────────────────────────────────────────
    WizardIO.header("Composizione pipeline — Stage")
    System.out.println(s"  Categorie disponibili: ${ANSI_CYAN}${StageCatalog.categories.mkString(" | ")}${ANSI_RESET}")
    System.out.println(s"  Digita il nome di uno stage, una categoria per filtrare, oppure Invio per terminare.\n")

    var addingStages = true
    var stageCount   = 0

    while (addingStages) {
      showCurrentPipeline(pd, stageCount)
      System.out.print(s"  ${ANSI_BOLD}Stage [${stageCount + 1}]${ANSI_RESET} (nome stage / categoria / Invio per finire): ")
      System.out.flush()
      val input = Option(scala.io.StdIn.readLine()).map(_.trim).getOrElse("")

      if (input.isEmpty) {
        addingStages = false
      } else if (StageCatalog.categories.exists(_.equalsIgnoreCase(input))) {
        // filtro per categoria: mostra lista e richiedi
        val inCat = StageCatalog.list(category = Some(input))
        System.out.println(s"\n  ${ANSI_BOLD}Stage in '$input':${ANSI_RESET}")
        inCat.zipWithIndex.foreach { case (s, i) =>
          val sName = s.getOrElse("name", "").toString
          val sDesc = s.getOrElse("description", "").toString.take(55)
          System.out.println(s"  [${i + 1}] ${ANSI_CYAN}$sName${ANSI_RESET} — $sDesc")
        }
        System.out.println()
        System.out.print("  Scegli (numero o nome, Invio per annullare): ")
        System.out.flush()
        val pick = Option(scala.io.StdIn.readLine()).map(_.trim).getOrElse("")
        if (pick.nonEmpty) {
          val stageName = try {
            val n = pick.toInt
            if (n >= 1 && n <= inCat.size) inCat(n - 1).getOrElse("name", "").toString else pick
          } catch { case _: NumberFormatException => pick }
          if (stageName.nonEmpty) addStageInteractive(pd, stageName, stageCount) match {
            case true  => stageCount += 1
            case false => // utente ha saltato
          }
        }
      } else {
        // nome stage diretto o ricerca
        val base    = StageCatalog.resolveBase(input)
        val matches = StageCatalog.list(search = Some(input))
        val stageName = if (StageCatalog.exists(base)) {
          base
        } else if (matches.size == 1) {
          val n = matches.head.getOrElse("name", "").toString
          System.out.println(s"  ${ANSI_YELLOW}Trovato: $n${ANSI_RESET}")
          n
        } else if (matches.size > 1) {
          System.out.println(s"\n  ${ANSI_BOLD}Risultati per '$input':${ANSI_RESET}")
          matches.take(8).zipWithIndex.foreach { case (s, i) =>
            System.out.println(s"  [${i + 1}] ${ANSI_CYAN}${s.getOrElse("name","")}${ANSI_RESET} — ${s.getOrElse("description","").toString.take(55)}")
          }
          System.out.print("  Scegli (numero o nome, Invio per annullare): ")
          System.out.flush()
          val pick = Option(scala.io.StdIn.readLine()).map(_.trim).getOrElse("")
          if (pick.isEmpty) "" else try {
            val n = pick.toInt
            if (n >= 1 && n <= matches.size) matches(n - 1).getOrElse("name", "").toString else pick
          } catch { case _: NumberFormatException => pick }
        } else {
          System.out.println(s"  ${ANSI_YELLOW}Stage '$input' non trovato nel catalogo — aggiungo comunque.${ANSI_RESET}")
          input
        }
        if (stageName.nonEmpty) addStageInteractive(pd, stageName, stageCount) match {
          case true  => stageCount += 1
          case false => // utente ha saltato
        }
      }
    }

    // ── 5. Output ──────────────────────────────────────────────────────────
    WizardIO.header("Output")
    val outputFormat = WizardIO.prompt("Formato (parquet / csv / json)", "parquet")
    val outputMode   = WizardIO.prompt("Modalità scrittura (overwrite / append)", "overwrite")
    YamlManifest.setOutput(pd, Some(outputFormat), Some(outputMode), None)

    // ── 6. Schedule ────────────────────────────────────────────────────────
    val schedCron = WizardIO.prompt("Schedule cron (es. '0 6 * * *', lascia vuoto per nessuno)")
    if (schedCron.nonEmpty) {
      val tz = WizardIO.prompt("Timezone", "Europe/Rome")
      YamlManifest.setSchedule(pd, schedCron, tz)
    }

    // ── 7. Salva e anteprima ───────────────────────────────────────────────
    YamlManifest.save(file, docs)
    System.out.println(s"\n${ANSI_BOLD}  Pipeline salvata: ${ANSI_CYAN}${file.getPath}${ANSI_RESET}")
    System.out.println(s"\n${ANSI_BOLD}  Anteprima YAML:${ANSI_RESET}")
    new String(java.nio.file.Files.readAllBytes(file.toPath), "UTF-8")
      .linesIterator.foreach(l => System.out.println("    " + l))
    WizardIO.showCommand(this, s"webrobot pipeline run -f ${file.getName} --follow")

    // ── 8. Apply ───────────────────────────────────────────────────────────
    if (!WizardIO.confirm("Applicare il manifest al server?")) {
      System.out.println(s"  ${ANSI_CYAN}webrobot pipeline apply -f ${file.getName}${ANSI_RESET}")
      return
    }
    val yamlContent = new String(java.nio.file.Files.readAllBytes(file.toPath), "UTF-8")
    System.out.println(s"  ${ANSI_CYAN}Applicazione manifest...${ANSI_RESET}")
    val applyBody = new java.util.HashMap[String, String]()
    applyBody.put("yaml", yamlContent)
    val applyNode = OpenApiHttp.postJson(apiClient(), "/webrobot/api/manifest/apply", applyBody)
    val projectId = findJsonField(applyNode, "projectId")
    val jobId     = findJsonField(applyNode, "jobId")
    if (projectId == null || jobId == null) {
      System.out.println(s"  ${ANSI_YELLOW}Manifest applicato ma projectId/jobId non estratti.${ANSI_RESET}")
      JsonCliUtil.printJson(applyNode)
      return
    }
    System.out.println(s"  ${ANSI_GREEN}✓ Applicato — progetto: $projectId  job: $jobId${ANSI_RESET}")

    // ── 9. Esegui ──────────────────────────────────────────────────────────
    if (!WizardIO.confirm("Avviare l'esecuzione ora?")) {
      System.out.println(s"  ${ANSI_CYAN}webrobot job execute -p $projectId -j $jobId --follow${ANSI_RESET}")
      return
    }
    val execPath = "/webrobot/api/projects/id/" + apiClient().escapeString(projectId) +
                  "/jobs/" + apiClient().escapeString(jobId) + "/execute"
    val execNode = OpenApiHttp.postJson(apiClient(), execPath,
                    com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode())
    val execId   = extractJsonField(execNode, "executionId", "id", "execution_id", "executionReferenceId")
    if (execId == null || execId.isEmpty) {
      System.out.println(s"  ${ANSI_YELLOW}Esecuzione avviata ma executionId non trovato.${ANSI_RESET}")
      JsonCliUtil.printJson(execNode)
      return
    }
    System.out.println(s"  ${ANSI_GREEN}✓ Esecuzione avviata — id: ${ANSI_BOLD}$execId${ANSI_RESET}")
    if (follow) followExecution(projectId, jobId, execId)
    else System.out.println(s"  ${ANSI_CYAN}webrobot job execute -p $projectId -j $jobId -F --follow${ANSI_RESET}")
  }

  // ── Aggiunge un singolo stage guidando parametro per parametro ────────────
  private def addStageInteractive(pd: scala.collection.mutable.Map[String, Any], stageName: String, idx: Int): Boolean = {
    implicit val self: BaseSubCommand = this
    val base       = StageCatalog.resolveBase(stageName)
    val stageDef   = StageCatalog.find(base)

    System.out.println()
    System.out.println(s"  ${ANSI_BOLD}Stage: ${ANSI_CYAN}$stageName${ANSI_RESET}")

    stageDef.foreach { s =>
      val desc = s.getOrElse("description", "").toString
      if (desc.nonEmpty) System.out.println(s"  $desc")
      s.get("example").foreach { ex =>
        System.out.println(s"  ${ANSI_BOLD}Esempio:${ANSI_RESET}")
        ex.toString.linesIterator.foreach(l => System.out.println(s"    $l"))
      }
    }
    System.out.println()

    // Raccoglie args parametro per parametro
    val argDefs: List[java.util.Map[String, Any]] = stageDef.flatMap(_.get("args")).collect {
      case l: java.util.List[_] =>
        l.asInstanceOf[java.util.List[java.util.Map[String, Any]]].asScala.toList
    }.getOrElse(List.empty)

    val collectedArgs = scala.collection.mutable.ListBuffer[String]()

    if (argDefs.nonEmpty) {
      System.out.println(s"  ${ANSI_BOLD}Parametri:${ANSI_RESET}")
      argDefs.foreach { argDef =>
        val aName = Option(argDef.get("name")).map(_.toString).getOrElse("")
        val aType = Option(argDef.get("type")).map(_.toString).getOrElse("string")
        val aDesc = Option(argDef.get("description")).map(_.toString).getOrElse("")
        val aReq  = Option(argDef.get("required")).exists(v => v.toString == "true")
        val aDefault = Option(argDef.get("default")).map(_.toString).getOrElse("")
        val reqMark  = if (aReq) s" ${ANSI_RED}*${ANSI_RESET}" else s" ${ANSI_YELLOW}(opzionale)${ANSI_RESET}"
        System.out.println(s"    ${ANSI_CYAN}$aName${ANSI_RESET} [$aType]$reqMark — $aDesc")
        val value = WizardIO.prompt(s"    $aName", aDefault)
        if (value.nonEmpty || aReq) collectedArgs += value
        else collectedArgs += "" // placeholder per args opzionali vuoti da rimuovere dopo
      }
    } else {
      // Stage senza schema args dichiarato — input libero
      System.out.println(s"  ${ANSI_YELLOW}Nessun schema parametri disponibile per questo stage.${ANSI_RESET}")
      val raw = WizardIO.prompt("  Args (virgola-separati, lascia vuoto se nessuno)")
      if (raw.nonEmpty) raw.split(",").map(_.trim).foreach(collectedArgs += _)
    }

    // Rimuovi trailing empty opzionali
    while (collectedArgs.nonEmpty && collectedArgs.last.isEmpty) collectedArgs.remove(collectedArgs.size - 1)

    val argsList = if (collectedArgs.nonEmpty) Some(collectedArgs.toList) else None

    // Mostra frammento YAML risultante
    System.out.println(s"\n  ${ANSI_BOLD}Frammento YAML:${ANSI_RESET}")
    System.out.print(s"    ${ANSI_CYAN}- stage: $stageName${ANSI_RESET}")
    argsList.foreach { args =>
      System.out.print(s"\n    ${ANSI_CYAN}  args: [${args.map(a => if (a.forall(c => c.isDigit || c == '.')) a else s""""$a"""").mkString(", ")}]${ANSI_RESET}")
    }
    System.out.println("\n")

    if (!WizardIO.confirm(s"Aggiungere lo stage '$stageName'?")) {
      System.out.println(s"  ${ANSI_YELLOW}Stage saltato.${ANSI_RESET}")
      return false
    }

    YamlManifest.addStage(pd, stageName, argsList, Map.empty, None, AtEnd)
    System.out.println(s"  ${ANSI_GREEN}✓ Stage '$stageName' aggiunto.${ANSI_RESET}\n")
    true
  }

  // ── Mostra pipeline corrente in forma compatta ────────────────────────────
  private def showCurrentPipeline(pd: scala.collection.mutable.Map[String, Any], count: Int): Unit = {
    if (count == 0) return
    val stages = YamlManifest.stageList(pd)
    System.out.println(s"  ${ANSI_BOLD}Pipeline attuale ($count stage):${ANSI_RESET}")
    (0 until stages.size()).foreach { i =>
      stages.get(i) match {
        case m: java.util.Map[_, _] =>
          val sm   = m.asInstanceOf[java.util.Map[String, Any]]
          val s    = Option(sm.get("stage")).map(_.toString).getOrElse("?")
          val args = Option(sm.get("args")).map(a => s"  args: $a").getOrElse("")
          System.out.println(s"  ${ANSI_CYAN}  [$i] $s${ANSI_RESET}$args")
        case _ =>
      }
    }
    System.out.println()
  }
}

// ─── wizard (gruppo) ──────────────────────────────────────────────────────────

@Command(
  name = "wizard",
  mixinStandardHelpOptions = true,
  sortOptions = false,
  description = Array("Wizard interattivi guidati per creare risorse passo-passo."),
  footer = Array(
    "",
    "Esempi:",
    "  webrobot wizard agent             -- crea un agent passo-passo",
    "  webrobot wizard pipeline          -- crea ed esegui una pipeline",
    "  webrobot wizard pipeline -f my.yaml --follow",
    ""
  ),
  subcommands = Array(
    classOf[AgentWizardCommand],
    classOf[PipelineWizardCommand]
  )
)
class RunWizardCommand extends Runnable {
  def run(): Unit = System.err.println("Uso: webrobot wizard <agent|pipeline>")
}
