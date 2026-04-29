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

    // Refresh catalogo stage (silenzioso)
    try { StageCatalog.fetchRemote(apiClient()) } catch { case _: Exception => }

    WizardIO.header("Wizard — Nuova Pipeline")

    // 1. Fetch progetti
    System.out.println(s"  ${ANSI_CYAN}Caricamento progetti...${ANSI_RESET}")
    val projNode = OpenApiHttp.getJson(apiClient(), "/webrobot/api/projects")
    val projects = if (projNode != null && projNode.isArray) projNode.elements().asScala.toList else List.empty
    if (projects.isEmpty) { System.out.println(s"  ${ANSI_RED}Nessun progetto trovato.${ANSI_RESET}"); return }
    System.out.println()
    projects.zipWithIndex.foreach { case (p, i) =>
      val id   = Option(p.get("id")).map(_.asText("")).getOrElse("")
      val name = Option(p.get("name")).map(_.asText("")).getOrElse(id)
      System.out.println(s"  [${i + 1}] $name  ${ANSI_CYAN}($id)${ANSI_RESET}")
    }
    System.out.println()

    // 2. Selezione progetto
    val projInput = WizardIO.prompt("Progetto (numero o id)")
    val projectRef = try {
      val n = projInput.toInt
      if (n >= 1 && n <= projects.size) Option(projects(n - 1).get("id")).map(_.asText("")).getOrElse("") else projInput
    } catch { case _: NumberFormatException => projInput }
    if (projectRef.isEmpty) { System.out.println(s"  ${ANSI_RED}Progetto non valido.${ANSI_RESET}"); return }

    // 3. Metadati pipeline
    val pipelineName = WizardIO.prompt("Nome pipeline")
    if (pipelineName.isEmpty) { System.out.println(s"  ${ANSI_RED}Nome obbligatorio.${ANSI_RESET}"); return }
    val pipelineDesc = WizardIO.prompt("Descrizione (opzionale)")

    // 4. File YAML output
    val yamlPath = WizardIO.prompt("File YAML", outputFile)
    val file = new File(yamlPath)

    // Inizializza il manifest in memoria
    val (docs, pd) = YamlManifest.ensurePipeline(file)
    val meta = pd.getOrElseUpdate("metadata", scala.collection.mutable.Map[String, Any]())
                 .asInstanceOf[scala.collection.mutable.Map[String, Any]]
    meta("name") = pipelineName
    if (pipelineDesc.nonEmpty) meta("description") = pipelineDesc
    val spec = pd.getOrElseUpdate("spec", scala.collection.mutable.Map[String, Any]())
                 .asInstanceOf[scala.collection.mutable.Map[String, Any]]
    spec("project") = projectRef

    // 5. Input dataset
    val inputDs = WizardIO.prompt("Dataset di input (id o lascia vuoto per saltare)")
    if (inputDs.nonEmpty) YamlManifest.setInput(pd, Some(inputDs), None)

    // 6. Aggiunta stage in loop
    WizardIO.header("Stage")
    System.out.println(s"  Stages disponibili: ${ANSI_CYAN}webrobot pipeline stages list${ANSI_RESET}")
    System.out.println(s"  Premi Invio senza nome per terminare l'aggiunta stage.\n")

    var addingStages = true
    var stageCount   = 0
    while (addingStages) {
      val stageName = WizardIO.prompt(s"Stage [${stageCount + 1}] (nome, es. fetch | intelligent_join | lascia vuoto per finire)")
      if (stageName.isEmpty) {
        addingStages = false
      } else {
        val base = StageCatalog.resolveBase(stageName)
        if (!StageCatalog.exists(base))
          System.out.println(s"  ${ANSI_YELLOW}Stage '$base' non nel catalogo — aggiungo comunque.${ANSI_RESET}")
        val argsStr = WizardIO.prompt(s"  Args per '$stageName' (virgola-separati, lascia vuoto se nessuno)")
        val argsList = if (argsStr.nonEmpty) Some(argsStr.split(",").map(_.trim).toList) else None
        YamlManifest.addStage(pd, stageName, argsList, Map.empty, None, AtEnd)
        stageCount += 1
        System.out.println(s"  ${ANSI_GREEN}✓ Stage '$stageName' aggiunto.${ANSI_RESET}")
      }
    }

    // 7. Output
    WizardIO.header("Output")
    val outputFormat = WizardIO.prompt("Formato output (parquet/csv/json)", "parquet")
    val outputMode   = WizardIO.prompt("Modalità scrittura (overwrite/append)", "overwrite")
    YamlManifest.setOutput(pd,
      if (outputFormat.nonEmpty) Some(outputFormat) else None,
      if (outputMode.nonEmpty)   Some(outputMode)   else None,
      None)

    // 8. Schedule (opzionale)
    val schedCron = WizardIO.prompt("Schedule cron (es. '0 6 * * *', lascia vuoto per nessuno)")
    if (schedCron.nonEmpty) {
      val tz = WizardIO.prompt("Timezone", "Europe/Rome")
      YamlManifest.setSchedule(pd, schedCron, tz)
    }

    // 9. Salva YAML e mostra anteprima
    YamlManifest.save(file, docs)
    System.out.println(s"\n  ${ANSI_GREEN}File YAML salvato: ${ANSI_BOLD}${file.getPath}${ANSI_RESET}")

    WizardIO.showCommand(this,
      s"webrobot pipeline run -f ${file.getName} --follow")

    // 10. Apply
    if (!WizardIO.confirm("Applicare il manifest al server?")) {
      System.out.println(s"\n  Per applicare in seguito:")
      System.out.println(s"  ${ANSI_CYAN}webrobot pipeline apply -f ${file.getName}${ANSI_RESET}")
      return
    }

    val yamlContent = new String(java.nio.file.Files.readAllBytes(file.toPath), "UTF-8")
    System.out.println(s"  ${ANSI_CYAN}Applicazione manifest...${ANSI_RESET}")
    val body = new java.util.HashMap[String, String]()
    body.put("yaml", yamlContent)
    val applyNode = OpenApiHttp.postJson(apiClient(), "/webrobot/api/manifest/apply", body)

    val projectId = findJsonField(applyNode, "projectId")
    val jobId     = findJsonField(applyNode, "jobId")

    if (projectId == null || jobId == null) {
      System.out.println(s"  ${ANSI_YELLOW}Manifest applicato ma projectId/jobId non estratti.${ANSI_RESET}")
      JsonCliUtil.printJson(applyNode)
      return
    }
    System.out.println(s"  ${ANSI_GREEN}✓ Applicato — progetto: $projectId  job: $jobId${ANSI_RESET}")

    // 11. Esegui
    if (!WizardIO.confirm("Avviare l'esecuzione ora?")) {
      System.out.println(s"\n  Per eseguire in seguito:")
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
    else {
      System.out.println(s"\n  Per seguire lo stato:")
      System.out.println(s"  ${ANSI_CYAN}webrobot job execute -p $projectId -j $jobId -F --follow${ANSI_RESET}")
    }
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
