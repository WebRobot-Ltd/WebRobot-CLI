package org.webrobot.cli.commands

import org.webrobot.cli.manifest.{StageCatalog, YamlManifest, AtEnd, AtStart, AtIndex, AfterName, BeforeName}
import org.webrobot.cli.openapi.{JsonCliUtil, OpenApiHttp}
import org.webrobot.cli.utils.DataGrid
import picocli.CommandLine.{Command, Option => Opt, Parameters}

import java.io.File
import java.nio.file.Files

// ─── stages list ──────────────────────────────────────────────────────────────

@Command(name = "list", sortOptions = false, description = Array("Elenca gli stage disponibili nel catalogo."))
class PipelineStagesListCommand extends BaseSubCommand {

  @Opt(names = Array("--category"), description = Array("Filtra per categoria (crawling|extraction|intelligent|utility|io|analytics|external-api|python|matching|use-case)"))
  private var category: String = ""

  @Opt(names = Array("--type"), description = Array("Filtra per tipo (source|transformation|sink|validation)"))
  private var extensionType: String = ""

  @Opt(names = Array("--search"), description = Array("Ricerca libera nel nome e descrizione"))
  private var search: String = ""

  override def startRun(): Unit = {
    val results = StageCatalog.list(
      if (category.nonEmpty) Some(category) else None,
      if (extensionType.nonEmpty) Some(extensionType) else None,
      if (search.nonEmpty) Some(search) else None
    )
    val dg = new DataGrid("NAME", "TYPE", "CATEGORY", "DESCRIPTION")
    results.foreach { s =>
      val subtypeNote = if (s.get("supportsSubtype").exists(_.toString == "true")) " [+subtype]" else ""
      dg.add(
        s.getOrElse("name", "").toString + subtypeNote,
        s.getOrElse("extensionType", "").toString,
        s.getOrElse("category", "").toString,
        s.getOrElse("description", "").toString.take(60)
      )
    }
    if (dg.size > 0) {
      dg.render
      System.out.println(s"${dg.size} stage disponibili. Usa 'webrobot pipeline stages describe <name>' per i dettagli.\n")
    } else {
      System.out.println("Nessuno stage trovato con i filtri specificati.")
    }
  }
}

// ─── stages describe ──────────────────────────────────────────────────────────

@Command(name = "describe", sortOptions = false, description = Array("Dettaglio e parametri di uno stage."))
class PipelineStagesDescribeCommand extends BaseSubCommand {

  @Parameters(index = "0", description = Array("Nome stage"))
  private var stageName: String = ""

  override def startRun(): Unit = {
    val base = StageCatalog.resolveBase(stageName)
    StageCatalog.find(base) match {
      case None =>
        System.out.println(s"Stage '$stageName' non trovato nel catalogo.")
        System.out.println(s"Stages disponibili: ${StageCatalog.list().map(_("name")).mkString(", ")}")
      case Some(s) =>
        System.out.println(s"Stage:       ${s.getOrElse("name", "")}")
        System.out.println(s"Tipo:        ${s.getOrElse("extensionType", "")} | Categoria: ${s.getOrElse("category", "")}")
        System.out.println(s"Descrizione: ${s.getOrElse("description", "")}")

        s.get("args") match {
          case Some(l: java.util.List[_]) if !l.isEmpty =>
            System.out.println("\nParametri:")
            val jList = l.asInstanceOf[java.util.List[java.util.Map[String, Any]]]
            for (i <- 0 until jList.size()) {
              val p = jList.get(i)
              val req = if (p.getOrDefault("required", false).toString == "true") " (required)" else ""
              System.out.println(s"  ${p.get("name")} [${p.get("type")}]$req — ${p.get("description")}")
            }
          case _ =>
            System.out.println("\nParametri: nessuno (o dipendono dal subtype)")
        }

        if (s.get("supportsSubtype").exists(_.toString == "true")) {
          System.out.println("\nSupporta subtypes — sintassi: stage: " + s("name") + ":<nome_funzione>")
        }

        s.get("example").foreach { ex =>
          System.out.println(s"\nEsempio YAML:\n")
          ex.toString.linesIterator.foreach(line => System.out.println("  " + line))
        }
    }
    System.out.println()
  }
}

// ─── stages (group) ───────────────────────────────────────────────────────────

@Command(
  name = "stages",
  mixinStandardHelpOptions = true,
  sortOptions = false,
  description = Array("Catalogo stage della piattaforma."),
  subcommands = Array(classOf[PipelineStagesListCommand], classOf[PipelineStagesDescribeCommand])
)
class PipelineStagesCommand extends Runnable {
  def run(): Unit = System.err.println("Uso: webrobot pipeline stages <list|describe>")
}

// ─── pipeline set ─────────────────────────────────────────────────────────────

@Command(name = "set", sortOptions = false, description = Array("Crea o aggiorna la sezione Pipeline nel file manifest YAML."))
class PipelineSetCommand extends BaseSubCommand {

  @Opt(names = Array("-f", "--file"), description = Array("File manifest YAML"), required = true)
  private var file: File = _

  @Opt(names = Array("-n", "--name"), description = Array("Nome della pipeline"))
  private var name: String = ""

  @Opt(names = Array("--project"), description = Array("Progetto (ID o ref:nome)"))
  private var project: String = ""

  @Opt(names = Array("--description"), description = Array("Descrizione"))
  private var description: String = ""

  override def startRun(): Unit = {
    val (docs, pd) = YamlManifest.ensurePipeline(file)
    val meta = pd.getOrElseUpdate("metadata", scala.collection.mutable.Map[String, Any]())
                 .asInstanceOf[scala.collection.mutable.Map[String, Any]]
    if (name.nonEmpty) meta("name") = name
    if (project.nonEmpty) {
      val spec = pd.getOrElseUpdate("spec", scala.collection.mutable.Map[String, Any]())
                   .asInstanceOf[scala.collection.mutable.Map[String, Any]]
      spec("project") = project
    }
    if (description.nonEmpty) meta("description") = description
    YamlManifest.save(file, docs)
    System.out.println(s"Pipeline aggiornata in ${file.getPath}")
  }
}

// ─── pipeline add-stage ───────────────────────────────────────────────────────

@Command(name = "add-stage", sortOptions = false, description = Array("Aggiunge uno stage alla pipeline nel file YAML."))
class PipelineAddStageCommand extends BaseSubCommand {

  @Parameters(index = "0", description = Array("Nome stage (es. fetch, python_row_transform:price_normalizer)"))
  private var stageName: String = ""

  @Opt(names = Array("-f", "--file"), description = Array("File manifest YAML"), required = true)
  private var file: File = _

  @Opt(names = Array("--args"), description = Array("Argomenti posizionali separati da virgola"))
  private var argsStr: String = ""

  @Opt(names = Array("--param"), description = Array("Parametro chiave=valore (ripetibile)"), arity = "0..*")
  private var params: Array[String] = Array.empty

  @Opt(names = Array("--name"), description = Array("Nome descrittivo dello stage"))
  private var displayName: String = ""

  @Opt(names = Array("--at"), description = Array("Posizione di inserimento (0-based)"))
  private var at: Int = -1

  @Opt(names = Array("--after"), description = Array("Inserisci dopo questo stage"))
  private var after: String = ""

  @Opt(names = Array("--before"), description = Array("Inserisci prima di questo stage"))
  private var before: String = ""

  override def startRun(): Unit = {
    // Valida stage nel catalogo
    val base = StageCatalog.resolveBase(stageName)
    if (!StageCatalog.exists(base)) {
      System.out.println(s"${ANSI_RED}WARN: Stage '$base' non trovato nel catalogo locale.${ANSI_RESET}")
      System.out.println("Procedo comunque (potrebbe essere uno stage custom o di un plugin).")
    }

    val (docs, pd) = YamlManifest.ensurePipeline(file)
    val stagesBefore = YamlManifest.stageList(pd).size()

    val argsList = if (argsStr.nonEmpty) Some(argsStr.split(",").map(_.trim).toList) else None
    val paramsMap = params.collect { case p if p.contains("=") =>
      val Array(k, v) = p.split("=", 2); k -> v
    }.toMap

    val position =
      if (after.nonEmpty) AfterName(after)
      else if (before.nonEmpty) BeforeName(before)
      else if (at >= 0) AtIndex(at)
      else AtEnd

    YamlManifest.addStage(pd, stageName, argsList, paramsMap, if (displayName.nonEmpty) Some(displayName) else None, position)
    YamlManifest.save(file, docs)

    val stagesAfter = YamlManifest.stageList(pd).size()
    val idx = stagesAfter - 1  // approximate
    System.out.println(s"${ANSI_GREEN}Stage '$stageName' aggiunto in posizione ${idx} (${stagesAfter} stage totali).${ANSI_RESET}")
    System.out.println(s"File: ${file.getPath}")
  }
}

// ─── pipeline remove-stage ────────────────────────────────────────────────────

@Command(name = "remove-stage", sortOptions = false, description = Array("Rimuove uno stage dalla pipeline (per indice 0-based o per nome)."))
class PipelineRemoveStageCommand extends BaseSubCommand {

  @Parameters(index = "0", description = Array("Indice (0-based) o nome stage"))
  private var target: String = ""

  @Opt(names = Array("-f", "--file"), description = Array("File manifest YAML"), required = true)
  private var file: File = _

  override def startRun(): Unit = {
    if (!file.exists()) { System.out.println(s"File non trovato: ${file.getPath}"); return }
    val docs = YamlManifest.load(file)
    YamlManifest.findPipeline(docs) match {
      case None => System.out.println("Nessuna sezione Pipeline trovata nel file.")
      case Some(pd) =>
        if (YamlManifest.removeStage(pd, target)) {
          YamlManifest.save(file, docs)
          System.out.println(s"Stage '$target' rimosso. File: ${file.getPath}")
        } else {
          System.out.println(s"Stage '$target' non trovato nella pipeline.")
        }
    }
  }
}

// ─── pipeline set-input ───────────────────────────────────────────────────────

@Command(name = "set-input", sortOptions = false, description = Array("Imposta il dataset/path di input nella Pipeline."))
class PipelineSetInputCommand extends BaseSubCommand {

  @Opt(names = Array("-f", "--file"), description = Array("File manifest YAML"), required = true)
  private var file: File = _

  @Opt(names = Array("--dataset"), description = Array("Dataset ID o ref:nome"))
  private var dataset: String = ""

  @Opt(names = Array("--path"), description = Array("Percorso S3A diretto (alternativo a --dataset)"))
  private var path: String = ""

  override def startRun(): Unit = {
    val (docs, pd) = YamlManifest.ensurePipeline(file)
    YamlManifest.setInput(pd, if (dataset.nonEmpty) Some(dataset) else None, if (path.nonEmpty) Some(path) else None)
    YamlManifest.save(file, docs)
    System.out.println(s"Input impostato in ${file.getPath}")
  }
}

// ─── pipeline set-output ──────────────────────────────────────────────────────

@Command(name = "set-output", sortOptions = false, description = Array("Imposta formato/path di output nella Pipeline."))
class PipelineSetOutputCommand extends BaseSubCommand {

  @Opt(names = Array("-f", "--file"), description = Array("File manifest YAML"), required = true)
  private var file: File = _

  @Opt(names = Array("--format"), description = Array("Formato output: parquet|csv|json (default parquet)"))
  private var format: String = ""

  @Opt(names = Array("--mode"), description = Array("Modalità scrittura: overwrite|append|ignore|error (default overwrite)"))
  private var mode: String = ""

  @Opt(names = Array("--path"), description = Array("Percorso S3A di output"))
  private var path: String = ""

  override def startRun(): Unit = {
    val (docs, pd) = YamlManifest.ensurePipeline(file)
    YamlManifest.setOutput(pd, if (format.nonEmpty) Some(format) else None, if (mode.nonEmpty) Some(mode) else None, if (path.nonEmpty) Some(path) else None)
    YamlManifest.save(file, docs)
    System.out.println(s"Output impostato in ${file.getPath}")
  }
}

// ─── pipeline set-schedule ────────────────────────────────────────────────────

@Command(name = "set-schedule", sortOptions = false, description = Array("Imposta lo schedule cron nella Pipeline."))
class PipelineSetScheduleCommand extends BaseSubCommand {

  @Opt(names = Array("-f", "--file"), description = Array("File manifest YAML"), required = true)
  private var file: File = _

  @Opt(names = Array("--cron"), description = Array("Espressione cron (es. '0 6 * * *')"), required = true)
  private var cron: String = ""

  @Opt(names = Array("--tz", "--timezone"), description = Array("Timezone (default Europe/Rome)"))
  private var timezone: String = "Europe/Rome"

  override def startRun(): Unit = {
    val (docs, pd) = YamlManifest.ensurePipeline(file)
    YamlManifest.setSchedule(pd, cron, timezone)
    YamlManifest.save(file, docs)
    System.out.println(s"Schedule '$cron' ($timezone) impostato in ${file.getPath}")
  }
}

// ─── pipeline show ────────────────────────────────────────────────────────────

@Command(name = "show", sortOptions = false, description = Array("Mostra la pipeline corrente dal file YAML."))
class PipelineShowCommand extends BaseSubCommand {

  @Opt(names = Array("-f", "--file"), description = Array("File manifest YAML"), required = true)
  private var file: File = _

  override def startRun(): Unit = {
    if (!file.exists()) { System.out.println(s"File non trovato: ${file.getPath}"); return }
    val docs = YamlManifest.load(file)
    YamlManifest.findPipeline(docs) match {
      case None => System.out.println("Nessuna sezione Pipeline trovata nel file.")
      case Some(pd) =>
        val stages = YamlManifest.stageList(pd)
        System.out.println(s"Pipeline — ${stages.size()} stage:")
        (0 until stages.size()).foreach { i =>
          stages.get(i) match {
            case m: java.util.Map[_, _] =>
              val sm = m.asInstanceOf[java.util.Map[String, Any]]
              val name = Option(sm.get("name")).map(n => " \"" + n + "\"").getOrElse("")
              val args = Option(sm.get("args")).map(a => s" args=$a").getOrElse("")
              System.out.println(s"  [$i] ${sm.get("stage")}$name$args")
            case other => System.out.println(s"  [$i] $other")
          }
        }
    }
  }
}

// ─── pipeline apply ───────────────────────────────────────────────────────────

@Command(name = "apply", sortOptions = false, description = Array("Applica il manifest YAML al server (crea o aggiorna risorse)."))
class PipelineApplyCommand extends BaseSubCommand {

  @Opt(names = Array("-f", "--file"), description = Array("File manifest YAML"), required = true)
  private var file: File = _

  @Opt(names = Array("--dry-run"), description = Array("Valida senza applicare"))
  private var dryRun: Boolean = false

  @Opt(names = Array("-y", "--yes"), description = Array("Salta conferma interattiva"))
  private var yes: Boolean = false

  override def startRun(): Unit = {
    this.init()
    if (!file.exists()) { System.out.println(s"File non trovato: ${file.getPath}"); return }
    val yamlContent = new String(Files.readAllBytes(file.toPath), "UTF-8")

    val endpoint = if (dryRun) "/webrobot/api/manifest/validate" else "/webrobot/api/manifest/apply"

    if (!yes && !dryRun) {
      System.out.print(s"Applicare '${file.getName}' al server? [y/N] ")
      val answer = scala.io.StdIn.readLine()
      if (answer == null || !answer.trim.equalsIgnoreCase("y")) {
        System.out.println("Annullato.")
        return
      }
    }

    val body = new java.util.HashMap[String, String]()
    body.put("yaml", yamlContent)
    val node = OpenApiHttp.postJson(apiClient(), endpoint, body)
    JsonCliUtil.printJson(node)
  }
}

// ─── pipeline validate ────────────────────────────────────────────────────────

@Command(name = "validate", sortOptions = false, description = Array("Valida il manifest YAML senza applicarlo al server."))
class PipelineValidateCommand extends BaseSubCommand {

  @Opt(names = Array("-f", "--file"), description = Array("File manifest YAML"), required = true)
  private var file: File = _

  override def startRun(): Unit = {
    this.init()
    if (!file.exists()) { System.out.println(s"File non trovato: ${file.getPath}"); return }
    val yamlContent = new String(Files.readAllBytes(file.toPath), "UTF-8")

    // Validazione locale: controlla stage nel catalogo
    System.out.println(s"Validazione locale di ${file.getName}...")
    val docs = YamlManifest.loadString(yamlContent)
    var errors = 0
    var warnings = 0
    docs.foreach { doc =>
      YamlManifest.findPipeline(List(doc)) match {
        case Some(pd) =>
          val stages = YamlManifest.stageList(pd)
          (0 until stages.size()).foreach { i =>
            stages.get(i) match {
              case m: java.util.Map[_, _] =>
                val sm = m.asInstanceOf[java.util.Map[String, Any]]
                val sName = Option(sm.get("stage")).map(_.toString).getOrElse("")
                val base = StageCatalog.resolveBase(sName)
                if (StageCatalog.exists(base)) {
                  System.out.println(s"  ${ANSI_GREEN}✓${ANSI_RESET} Stage '$sName' trovato nel catalogo")
                } else {
                  System.out.println(s"  ${ANSI_RED}✗${ANSI_RESET} Stage '$sName' NON trovato nel catalogo (potrebbe essere un plugin custom)")
                  warnings += 1
                }
              case _ =>
            }
          }
        case None =>
      }
    }

    if (errors == 0 && warnings == 0)
      System.out.println(s"\n${ANSI_GREEN}Validazione locale OK.${ANSI_RESET}")
    else
      System.out.println(s"\n$errors errori, $warnings warning.")

    // Validazione remota opzionale
    try {
      val body = new java.util.HashMap[String, String]()
      body.put("yaml", yamlContent)
      val node = OpenApiHttp.postJson(apiClient(), "/webrobot/api/manifest/validate", body)
      if (node != null) { System.out.println("\nValidazione remota:"); JsonCliUtil.printJson(node) }
    } catch {
      case _: Exception => System.out.println("(validazione remota non disponibile)")
    }
  }
}

// ─── pipeline (group) ─────────────────────────────────────────────────────────

@Command(
  name = "pipeline",
  mixinStandardHelpOptions = true,
  sortOptions = false,
  description = Array(
    "Comandi per la gestione declarativa delle pipeline YAML.",
    "Il file YAML è la fonte di verità — modificato localmente, poi applicato con 'apply'."
  ),
  footer = Array(
    "",
    "Workflow tipico:",
    "  webrobot pipeline set --name my-pipeline -f pipeline.yaml",
    "  webrobot pipeline stages list --category crawling",
    "  webrobot pipeline add-stage fetch --args \"https://example.com\" -f pipeline.yaml",
    "  webrobot pipeline add-stage intelligentJoin --args \"auto,none,20\" -f pipeline.yaml",
    "  webrobot pipeline set-output --format parquet -f pipeline.yaml",
    "  webrobot pipeline validate -f pipeline.yaml",
    "  webrobot pipeline apply -f pipeline.yaml",
    ""
  ),
  subcommands = Array(
    classOf[PipelineSetCommand],
    classOf[PipelineAddStageCommand],
    classOf[PipelineRemoveStageCommand],
    classOf[PipelineSetInputCommand],
    classOf[PipelineSetOutputCommand],
    classOf[PipelineSetScheduleCommand],
    classOf[PipelineShowCommand],
    classOf[PipelineApplyCommand],
    classOf[PipelineValidateCommand],
    classOf[PipelineStagesCommand]
  )
)
class RunPipelineCommand extends Runnable {
  def run(): Unit = System.err.println(
    "Uso: webrobot pipeline <sottocomando>\n" +
    "Sottocomandi: set | add-stage | remove-stage | set-input | set-output | set-schedule | show | apply | validate | stages"
  )
}
