package org.webrobot.cli.commands

import eu.webrobot.openapi.client.model.{AgentDto, JobDto}
import org.webrobot.cli.manifest.{AtEnd, StageCatalog, YamlManifest}
import org.webrobot.cli.openapi.{JsonCliUtil, OpenApiHttp}
import picocli.CommandLine.{Command, Option => Opt}

import java.io.File
import scala.collection.JavaConverters._
import scala.collection.mutable.ListBuffer

// ─── IO helpers ───────────────────────────────────────────────────────────────

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

// ─── shared stage composer ────────────────────────────────────────────────────

private object StageWizardHelper {

  def collectStageArgs(stageName: String)(implicit cmd: BaseSubCommand): Option[(String, List[String])] = {
    import cmd._
    val base     = StageCatalog.resolveBase(stageName)
    val stageDef = StageCatalog.find(base)

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

    val argDefs: List[java.util.Map[String, Any]] = stageDef.flatMap(_.get("args")).collect {
      case l: java.util.List[_] =>
        l.asInstanceOf[java.util.List[java.util.Map[String, Any]]].asScala.toList
    }.getOrElse(List.empty)

    val collected = ListBuffer[String]()

    if (argDefs.nonEmpty) {
      System.out.println(s"  ${ANSI_BOLD}Parametri:${ANSI_RESET}")
      argDefs.foreach { argDef =>
        val aName    = Option(argDef.get("name")).map(_.toString).getOrElse("")
        val aType    = Option(argDef.get("type")).map(_.toString).getOrElse("string")
        val aDesc    = Option(argDef.get("description")).map(_.toString).getOrElse("")
        val aReq     = Option(argDef.get("required")).exists(_.toString == "true")
        val aDefault = Option(argDef.get("default")).map(_.toString).getOrElse("")
        val reqMark  = if (aReq) s" ${ANSI_RED}*${ANSI_RESET}" else s" ${ANSI_YELLOW}(opzionale)${ANSI_RESET}"
        System.out.println(s"    ${ANSI_CYAN}$aName${ANSI_RESET} [$aType]$reqMark — $aDesc")
        val value = WizardIO.prompt(s"    $aName", aDefault)
        collected += (if (value.nonEmpty || aReq) value else "")
      }
    } else {
      System.out.println(s"  ${ANSI_YELLOW}Nessun schema parametri disponibile.${ANSI_RESET}")
      val raw = WizardIO.prompt("  Args (virgola-separati, lascia vuoto se nessuno)")
      if (raw.nonEmpty) raw.split(",").map(_.trim).foreach(collected += _)
    }

    while (collected.nonEmpty && collected.last.isEmpty) collected.remove(collected.size - 1)

    val args = collected.toList
    System.out.println(s"\n  ${ANSI_BOLD}Frammento YAML:${ANSI_RESET}")
    System.out.print(s"    ${ANSI_CYAN}- stage: $stageName${ANSI_RESET}")
    if (args.nonEmpty)
      System.out.print(s"\n    ${ANSI_CYAN}  args: [${args.map(a => if (a.matches("[0-9]+\\.?[0-9]*")) a else s""""$a"""").mkString(", ")}]${ANSI_RESET}")
    System.out.println("\n")

    if (!WizardIO.confirm(s"Aggiungere lo stage '$stageName'?")) {
      System.out.println(s"  ${ANSI_YELLOW}Stage saltato.${ANSI_RESET}"); None
    } else Some((stageName, args))
  }

  def pickFromList(items: List[Map[String, AnyRef]]): String = {
    System.out.print(s"  Scegli (numero o nome, Invio per annullare): ")
    System.out.flush()
    val pick = Option(scala.io.StdIn.readLine()).map(_.trim).getOrElse("")
    if (pick.isEmpty) "" else try {
      val n = pick.toInt
      if (n >= 1 && n <= items.size) items(n - 1).getOrElse("name", "").toString else pick
    } catch { case _: NumberFormatException => pick }
  }

  def resolveStageInput(input: String)(implicit cmd: BaseSubCommand): String = {
    import cmd._
    if (StageCatalog.categories.exists(_.equalsIgnoreCase(input))) {
      val inCat = StageCatalog.list(category = Some(input))
      System.out.println(s"\n  ${ANSI_BOLD}Stage in '$input':${ANSI_RESET}")
      inCat.zipWithIndex.foreach { case (s, i) =>
        System.out.println(s"  [${i + 1}] ${ANSI_CYAN}${s.getOrElse("name", "")}${ANSI_RESET} — ${s.getOrElse("description", "").toString.take(55)}")
      }
      System.out.println()
      pickFromList(inCat)
    } else {
      val base    = StageCatalog.resolveBase(input)
      val matches = StageCatalog.list(search = Some(input))
      if (StageCatalog.exists(base)) base
      else if (matches.size == 1) {
        val n = matches.head.getOrElse("name", "").toString
        System.out.println(s"  ${ANSI_YELLOW}Trovato: $n${ANSI_RESET}"); n
      } else if (matches.size > 1) {
        System.out.println(s"\n  ${ANSI_BOLD}Risultati per '$input':${ANSI_RESET}")
        matches.take(8).zipWithIndex.foreach { case (s, i) =>
          System.out.println(s"  [${i + 1}] ${ANSI_CYAN}${s.getOrElse("name", "")}${ANSI_RESET} — ${s.getOrElse("description", "").toString.take(55)}")
        }
        pickFromList(matches)
      } else {
        System.out.println(s"  ${ANSI_YELLOW}Stage '$input' non trovato nel catalogo — aggiungo comunque.${ANSI_RESET}")
        input
      }
    }
  }

  def stagesToYaml(stages: List[(String, List[String])]): String = {
    val sb = new StringBuilder("stages:\n")
    stages.foreach { case (name, args) =>
      sb.append(s"  - stage: $name\n")
      if (args.nonEmpty) {
        sb.append("    args:\n")
        args.foreach { a =>
          val v = if (a.matches("[0-9]+\\.?[0-9]*")) a else s""""$a""""
          sb.append(s"      - $v\n")
        }
      }
    }
    sb.toString
  }
}

// ─── dataset wizard trait ─────────────────────────────────────────────────────

trait DatasetWizard { self: BaseSubCommand =>

  private val colPattern = java.util.regex.Pattern.compile("\\$(?!\\{)([a-zA-Z_][a-zA-Z0-9_]*)")

  protected def extractColumns(text: String): List[String] = {
    val m = colPattern.matcher(text)
    val cols = scala.collection.mutable.LinkedHashSet[String]()
    while (m.find()) cols += m.group(1)
    cols.toList
  }

  protected def buildCsvInteractive(columns: List[String]): String = {
    implicit val _cmd: BaseSubCommand = self
    val sb = new StringBuilder(columns.mkString(",") + "\n")
    var rowNum = 1
    var adding = true
    while (adding) {
      System.out.println(s"  ${ANSI_BOLD}Riga $rowNum${ANSI_RESET} — Invio vuoto sul primo campo per terminare:")
      val vals = columns.map(col => WizardIO.prompt(s"    $col"))
      if (vals.head.isEmpty) {
        adding = false
      } else {
        val escaped = vals.map(v =>
          if (v.contains(",") || v.contains("\"")) s""""${v.replace("\"", "\"\"")}"""" else v)
        sb.append(escaped.mkString(",") + "\n")
        rowNum += 1
      }
    }
    sb.toString
  }

  // Selects a category → agent → fetches agent code. Returns (categoryId, agentId, agentCode).
  protected def selectAgentWithCode(): (String, String, String) = {
    implicit val _cmd: BaseSubCommand = this
    System.out.println(s"  ${ANSI_CYAN}Caricamento categorie...${ANSI_RESET}")
    val catsNode = OpenApiHttp.getJson(apiClient(), "/webrobot/api/categories")
    val cats     = if (catsNode != null && catsNode.isArray) catsNode.elements().asScala.toList else List.empty
    System.out.println()
    cats.zipWithIndex.foreach { case (c, i) =>
      val id   = Option(c.get("id")).map(_.asText("")).getOrElse("")
      val name = Option(c.get("name")).map(_.asText("")).getOrElse(id)
      System.out.println(s"  [${i + 1}] $name  ${ANSI_CYAN}($id)${ANSI_RESET}")
    }
    System.out.println()
    val catPick = WizardIO.prompt("Categoria agent (numero o id, Invio per inserire id diretto)")

    val (agentId, catId) = if (catPick.nonEmpty) {
      val cid = try {
        val n = catPick.toInt
        if (n >= 1 && n <= cats.size) Option(cats(n - 1).get("id")).map(_.asText("")).getOrElse("") else catPick
      } catch { case _: NumberFormatException => catPick }
      val agentsNode = OpenApiHttp.getJson(apiClient(), "/webrobot/api/agents/" + apiClient().escapeString(cid))
      val agents     = if (agentsNode != null && agentsNode.isArray) agentsNode.elements().asScala.toList else List.empty
      if (agents.isEmpty) {
        System.out.println(s"  ${ANSI_YELLOW}Nessun agent in questa categoria.${ANSI_RESET}")
        (WizardIO.prompt("Agent id (manuale)"), cid)
      } else {
        System.out.println()
        agents.zipWithIndex.foreach { case (a, i) =>
          val id   = Option(a.get("id")).map(_.asText("")).getOrElse("")
          val name = Option(a.get("name")).map(_.asText("")).getOrElse(id)
          System.out.println(s"  [${i + 1}] ${ANSI_BOLD}$name${ANSI_RESET}  ${ANSI_CYAN}($id)${ANSI_RESET}")
        }
        System.out.println()
        val agPick = WizardIO.prompt("Agent (numero o id)")
        val aid = try {
          val n = agPick.toInt
          if (n >= 1 && n <= agents.size) Option(agents(n - 1).get("id")).map(_.asText("")).getOrElse("") else agPick
        } catch { case _: NumberFormatException => agPick }
        (aid, cid)
      }
    } else {
      val cid2 = WizardIO.prompt("Categoria id")
      val aid  = WizardIO.prompt("Agent id")
      (aid, cid2)
    }

    val code: String = try {
      val path = "/webrobot/api/agents/" + apiClient().escapeString(catId) +
                 "/" + apiClient().escapeString(agentId)
      val node = OpenApiHttp.getJson(apiClient(), path)
      if (node != null) Option(node.get("code")).map(_.asText("")).getOrElse("") else ""
    } catch { case _: Exception => "" }

    (catId, agentId, code)
  }

  // stageText: raw string representation of all stage args (scanned for $col patterns)
  // suggestedName: used as dataset name prefix
  // Returns Some(datasetId) or None if skipped
  protected def runDatasetWizard(stageText: String, suggestedName: String): Option[String] = {
    implicit val _cmd: BaseSubCommand = self
    val cols = extractColumns(stageText)
    WizardIO.header("Dataset di input")

    if (cols.isEmpty) {
      // No $col variables found — auto-generate single-row trigger dataset
      System.out.println(s"  ${ANSI_YELLOW}Nessuna variabile \$$col trovata negli stage.${ANSI_RESET}")
      System.out.println(s"  Il job richiede un dataset di input anche senza variabili.")
      System.out.println(s"  Creo un dataset trigger con una sola riga (valore arbitrario).\n")
      val name = WizardIO.prompt("Nome dataset di default", suggestedName + "-default-input")
      System.out.println(s"  ${ANSI_CYAN}Upload...${ANSI_RESET}")
      val csv = "_trigger\n1\n"
      val result = uploadDatasetCsvMultipart(name, csv.getBytes("UTF-8"))
      result.foreach(id => System.out.println(s"  ${ANSI_GREEN}✓ Dataset trigger — id: $id${ANSI_RESET}"))
      result

    } else {
      System.out.println(s"  Variabili trovate: ${cols.map(c => s"${ANSI_CYAN}$$$c${ANSI_RESET}").mkString("  ")}")
      System.out.println(s"  Colonne richieste: ${ANSI_BOLD}${cols.mkString(", ")}${ANSI_RESET}")
      System.out.println(s"  Ogni riga del dataset = un'iterazione del workflow.\n")
      System.out.println(s"  ${ANSI_CYAN}[1]${ANSI_RESET} Costruisci nuovo dataset (inserisci righe)")
      System.out.println(s"  ${ANSI_CYAN}[2]${ANSI_RESET} Usa dataset esistente (valida colonne)")
      System.out.println(s"  ${ANSI_CYAN}[3]${ANSI_RESET} Salta\n")
      val choice = WizardIO.prompt("Scelta", "1")

      choice match {
        case "2" =>
          val dsNode = OpenApiHttp.getJson(self.apiClient(), "/webrobot/api/datasets")
          val dsList = if (dsNode != null && dsNode.isArray) dsNode.elements().asScala.toList else List.empty
          if (dsList.isEmpty) {
            System.out.println(s"  ${ANSI_YELLOW}Nessun dataset trovato.${ANSI_RESET}"); None
          } else {
            System.out.println()
            dsList.zipWithIndex.foreach { case (d, i) =>
              val id   = Option(d.get("id")).map(_.asText("")).getOrElse("")
              val name = Option(d.get("name")).map(_.asText("")).getOrElse(id)
              System.out.println(s"  [${i + 1}] ${ANSI_BOLD}$name${ANSI_RESET}  ${ANSI_CYAN}($id)${ANSI_RESET}")
            }
            System.out.println()
            val pick = WizardIO.prompt("Dataset (numero o id)")
            val dsId = try {
              val n = pick.toInt
              if (n >= 1 && n <= dsList.size) Option(dsList(n - 1).get("id")).map(_.asText("")).getOrElse("") else pick
            } catch { case _: NumberFormatException => pick }
            if (dsId.isEmpty) None
            else {
              System.out.println(s"\n  ${ANSI_YELLOW}Assicurati che il dataset contenga le colonne: ${ANSI_BOLD}${cols.mkString(", ")}${ANSI_RESET}")
              if (WizardIO.confirm("Le colonne sono presenti e corrette?")) Some(dsId) else None
            }
          }

        case "3" => None

        case _ =>
          val csv       = buildCsvInteractive(cols)
          val rowCount  = csv.linesIterator.count(_.trim.nonEmpty) - 1
          if (rowCount <= 0) {
            System.out.println(s"  ${ANSI_YELLOW}Nessuna riga inserita, dataset saltato.${ANSI_RESET}"); None
          } else {
            val name = WizardIO.prompt("Nome dataset", suggestedName + "-input")
            System.out.println(s"  ${ANSI_CYAN}Upload $rowCount riga/righe...${ANSI_RESET}")
            val result = uploadDatasetCsvMultipart(name, csv.getBytes("UTF-8"))
            result.foreach(id => System.out.println(s"  ${ANSI_GREEN}✓ Dataset caricato ($rowCount righe) — id: $id${ANSI_RESET}"))
            result
          }
      }
    }
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

    // 1. Categorie
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

    // 4. Codice dell'agent
    WizardIO.header("Codice dell'agent")
    System.out.println(s"  ${ANSI_CYAN}[1]${ANSI_RESET} Componi interattivamente (wizard stage)")
    System.out.println(s"  ${ANSI_CYAN}[2]${ANSI_RESET} Fornisci un file YAML esistente")
    System.out.println(s"  ${ANSI_CYAN}[3]${ANSI_RESET} Salta (aggiungi il codice dopo)\n")
    val codeChoice = WizardIO.prompt("Scelta", "1")

    var savedCodeFile: Option[String] = None

    val codeContent: Option[String] = codeChoice match {

      case "2" =>
        val codeFile = WizardIO.prompt("File codice (path)")
        if (codeFile.nonEmpty) {
          val f = new File(codeFile)
          if (!f.exists()) {
            System.out.println(s"  ${ANSI_YELLOW}File non trovato, codice saltato.${ANSI_RESET}"); None
          } else {
            savedCodeFile = Some(codeFile)
            Some(scala.io.Source.fromFile(f).getLines().mkString("\r\n"))
          }
        } else None

      case "3" => None

      case _ =>
        try { StageCatalog.fetchRemote(apiClient()) } catch { case _: Exception => }
        System.out.println(s"\n  Categorie: ${ANSI_CYAN}${StageCatalog.categories.mkString(" | ")}${ANSI_RESET}")
        System.out.println(s"  Digita nome stage o categoria; Invio vuoto per terminare.\n")

        val collectedStages = ListBuffer[(String, List[String])]()
        var adding = true

        while (adding) {
          if (collectedStages.nonEmpty) {
            System.out.println(s"  ${ANSI_BOLD}Stage aggiunti:${ANSI_RESET}")
            collectedStages.zipWithIndex.foreach { case ((n, a), i) =>
              val argsStr = if (a.nonEmpty) s"  args: [${a.mkString(", ")}]" else ""
              System.out.println(s"  ${ANSI_CYAN}  [${i + 1}] $n${ANSI_RESET}$argsStr")
            }
            System.out.println()
          }
          System.out.print(s"  ${ANSI_BOLD}Stage [${collectedStages.size + 1}]${ANSI_RESET} (nome / categoria / Invio per finire): ")
          System.out.flush()
          val input = Option(scala.io.StdIn.readLine()).map(_.trim).getOrElse("")
          if (input.isEmpty) {
            adding = false
          } else {
            val stageName = StageWizardHelper.resolveStageInput(input)
            if (stageName.nonEmpty) {
              StageWizardHelper.collectStageArgs(stageName) match {
                case Some(entry) =>
                  collectedStages += entry
                  System.out.println(s"  ${ANSI_GREEN}✓ Stage '${entry._1}' aggiunto.${ANSI_RESET}\n")
                case None =>
              }
            }
          }
        }

        if (collectedStages.isEmpty) None
        else {
          val yaml = StageWizardHelper.stagesToYaml(collectedStages.toList)
          System.out.println(s"\n  ${ANSI_BOLD}Codice YAML generato:${ANSI_RESET}")
          yaml.linesIterator.foreach(l => System.out.println("    " + l))
          val saveFile = WizardIO.prompt("\n  Salva codice in file (lascia vuoto per non salvare)")
          if (saveFile.nonEmpty) {
            val f = new File(saveFile)
            java.nio.file.Files.write(f.toPath, yaml.getBytes("UTF-8"))
            System.out.println(s"  ${ANSI_GREEN}Salvato: ${f.getPath}${ANSI_RESET}")
            savedCodeFile = Some(saveFile)
          }
          Some(yaml)
        }
    }

    // 5. Comando equivalente e conferma
    val fileFlag = savedCodeFile.map(f => s" -f $f").getOrElse("")
    val descFlag = if (agentDesc.nonEmpty) s""" -d "$agentDesc"""" else ""
    WizardIO.showCommand(this, s"""webrobot agent add -c $categoryId -n "$agentName"$descFlag$fileFlag""")

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
    System.out.println(s"  ${ANSI_CYAN}webrobot wizard job${ANSI_RESET}  — crea il job e il dataset di input")
  }
}

// ─── pipeline wizard ──────────────────────────────────────────────────────────

@Command(
  name = "pipeline",
  sortOptions = false,
  description = Array("Wizard interattivo per creare ed eseguire una pipeline.")
)
class PipelineWizardCommand extends BaseSubCommand with DatasetWizard {

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

    // ── 3. Stage loop guidato ──────────────────────────────────────────────
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
      } else {
        val stageName = StageWizardHelper.resolveStageInput(input)
        if (stageName.nonEmpty && addStageToManifest(pd, stageName)) stageCount += 1
      }
    }

    // ── 4. Dataset di input (analisi $col dagli stage) ─────────────────────
    val stageText = {
      val sl = YamlManifest.stageList(pd)
      (0 until sl.size()).map(sl.get).mkString(" ")
    }
    val inputDatasetId = runDatasetWizard(stageText, pipelineName)
    inputDatasetId.foreach(id => YamlManifest.setInput(pd, Some(id), None))

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

  private def addStageToManifest(pd: scala.collection.mutable.Map[String, Any], stageName: String): Boolean = {
    implicit val self: BaseSubCommand = this
    StageWizardHelper.collectStageArgs(stageName) match {
      case Some((name, args)) =>
        YamlManifest.addStage(pd, name, if (args.nonEmpty) Some(args) else None, Map.empty, None, AtEnd)
        System.out.println(s"  ${ANSI_GREEN}✓ Stage '$name' aggiunto.${ANSI_RESET}\n")
        true
      case None => false
    }
  }

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

// ─── job wizard ───────────────────────────────────────────────────────────────

@Command(
  name = "job",
  sortOptions = false,
  description = Array("Wizard interattivo per creare un job, configurare il dataset di input ed eseguire.")
)
class JobWizardCommand extends BaseSubCommand with DatasetWizard {

  @Opt(names = Array("-F", "--follow"), description = Array("Segui l'esecuzione dopo l'avvio"))
  private var follow: Boolean = false

  override def startRun(): Unit = {
    this.init()
    implicit val self: BaseSubCommand = this

    WizardIO.header("Wizard — Nuovo Job")

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
    val projInput = WizardIO.prompt("Progetto (numero o id)")
    val projectId = try {
      val n = projInput.toInt
      if (n >= 1 && n <= projects.size) Option(projects(n - 1).get("id")).map(_.asText("")).getOrElse("") else projInput
    } catch { case _: NumberFormatException => projInput }
    if (projectId.isEmpty) { System.out.println(s"  ${ANSI_RED}Progetto non valido.${ANSI_RESET}"); return }

    // ── 2. Agent (per categoria) + fetch codice per $col ──────────────────
    System.out.println()
    val (_, agentId, agentCode) = selectAgentWithCode()
    if (agentId.isEmpty) { System.out.println(s"  ${ANSI_RED}Agent obbligatorio.${ANSI_RESET}"); return }

    // ── 4. Nome e descrizione job ──────────────────────────────────────────
    System.out.println()
    val jobName = WizardIO.prompt("Nome job")
    if (jobName.isEmpty) { System.out.println(s"  ${ANSI_RED}Nome obbligatorio.${ANSI_RESET}"); return }
    val jobDesc = WizardIO.prompt("Descrizione (opzionale)")

    // ── 5. Dataset wizard ──────────────────────────────────────────────────
    val datasetId = runDatasetWizard(agentCode, jobName)
    if (datasetId.isEmpty) {
      System.out.println(s"  ${ANSI_YELLOW}Dataset non configurato — impossibile creare il job.${ANSI_RESET}"); return
    }

    // ── 6. Crea job ────────────────────────────────────────────────────────
    WizardIO.showCommand(this,
      s"""webrobot job add -p $projectId -n "$jobName" -a $agentId -i ${datasetId.get}""")
    if (!WizardIO.confirm("Creare il job?")) { System.out.println("  Annullato."); return }

    val dto = new JobDto()
    dto.setProjectId(projectId)
    dto.setName(jobName)
    if (jobDesc.nonEmpty) dto.setDescription(jobDesc)
    dto.setAgentId(agentId)
    dto.setInputDatasetId(datasetId.get)
    val jobNode = OpenApiHttp.postJson(apiClient(),
      "/webrobot/api/projects/id/" + apiClient().escapeString(projectId) + "/jobs", dto)
    val jobId = if (jobNode != null) Option(jobNode.get("id")).map(_.asText("")).getOrElse("") else ""
    if (jobId.isEmpty) {
      System.out.println(s"  ${ANSI_YELLOW}Job creato ma id non estratto.${ANSI_RESET}")
      JsonCliUtil.printJson(jobNode)
      return
    }
    System.out.println(s"\n  ${ANSI_GREEN}✓ Job creato — id: ${ANSI_BOLD}$jobId${ANSI_RESET}")

    // ── 7. Esegui? ─────────────────────────────────────────────────────────
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
    else System.out.println(s"  ${ANSI_CYAN}webrobot job execute -p $projectId -j $jobId --follow${ANSI_RESET}")
  }
}

// ─── dataset wizard ───────────────────────────────────────────────────────────

@Command(
  name = "dataset",
  sortOptions = false,
  description = Array("Wizard interattivo per creare e caricare un dataset di input, guidato dall'agent di riferimento.")
)
class DatasetWizardCommand extends BaseSubCommand with DatasetWizard {

  override def startRun(): Unit = {
    this.init()
    implicit val self: BaseSubCommand = this

    WizardIO.header("Wizard — Dataset di Input")
    System.out.println(s"  Seleziona l'agent di riferimento per rilevare le variabili \$$col.\n")

    val (_, _, agentCode) = selectAgentWithCode()

    System.out.println()
    val dsName = WizardIO.prompt("Nome dataset", "input-dataset")

    val dsId = runDatasetWizard(agentCode, dsName)
    dsId match {
      case Some(id) =>
        System.out.println(s"\n  ${ANSI_GREEN}Dataset pronto!${ANSI_RESET}")
        System.out.println(s"  id: ${ANSI_BOLD}$id${ANSI_RESET}")
        System.out.println(s"\n  Prossimi passi:")
        System.out.println(s"  ${ANSI_CYAN}webrobot wizard job${ANSI_RESET}  — crea il job associando questo dataset")
        System.out.println(s"  ${ANSI_CYAN}webrobot job add -p <projectId> -n <nome> -a <agentId> -i $id${ANSI_RESET}")
      case None =>
        System.out.println(s"  ${ANSI_YELLOW}Nessun dataset creato.${ANSI_RESET}")
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
    "  webrobot wizard agent             -- crea un agent con stage composer",
    "  webrobot wizard pipeline          -- crea pipeline + dataset + esegui",
    "  webrobot wizard job               -- crea job + dataset di input + esegui",
    "  webrobot wizard dataset           -- crea/carica dataset guidato dall'agent",
    "  webrobot wizard pipeline -f my.yaml --follow",
    ""
  ),
  subcommands = Array(
    classOf[AgentWizardCommand],
    classOf[PipelineWizardCommand],
    classOf[JobWizardCommand],
    classOf[DatasetWizardCommand]
  )
)
class RunWizardCommand extends Runnable {
  def run(): Unit = System.err.println("Uso: webrobot wizard <agent|pipeline|job|dataset>")
}
