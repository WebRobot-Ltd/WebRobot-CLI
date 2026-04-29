package org.webrobot.cli.commands

import org.webrobot.cli.manifest.{StageCatalog, YamlManifest, AtEnd, AtStart, AtIndex, AfterName, BeforeName}
import org.webrobot.cli.openapi.{JsonCliUtil, OpenApiHttp}
import org.webrobot.cli.utils.DataGrid
import picocli.CommandLine.{Command, Option => Opt, Parameters}

import java.io.File
import java.nio.file.Files
import scala.collection.JavaConverters._

// ─── stages list ──────────────────────────────────────────────────────────────

@Command(name = "list", sortOptions = false, description = Array("Elenca gli stage disponibili nel catalogo (remoto se auth configurata, locale come fallback)."))
class PipelineStagesListCommand extends BaseSubCommand {

  @Opt(names = Array("--category"), description = Array("Filtra per categoria (crawling|extraction|intelligent|utility|io|analytics|external-api|python|matching|use-case)"))
  private var category: String = ""

  @Opt(names = Array("--type"), description = Array("Filtra per tipo (source|transformation|sink|validation)"))
  private var extensionType: String = ""

  @Opt(names = Array("--search"), description = Array("Ricerca libera nel nome e descrizione"))
  private var search: String = ""

  override def startRun(): Unit = {
    // Try to refresh remote catalog (fail silently)
    try {
      this.init()
      val refreshed = StageCatalog.fetchRemote(apiClient())
      if (!refreshed && !StageCatalog.isUsingRemote) {
        System.out.println(s"${ANSI_YELLOW}(catalogo locale — connetti e configura auth per il catalogo dinamico)${ANSI_RESET}")
      }
    } catch { case _: Exception => }

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
      val source = if (StageCatalog.isUsingRemote) s"${ANSI_GREEN}remoto${ANSI_RESET}" else s"${ANSI_YELLOW}locale${ANSI_RESET}"
      dg.render
      System.out.println(s"${dg.size} stage (catalogo $source). Usa 'webrobot pipeline stages describe <name>' per i dettagli.\n")
    } else {
      System.out.println("Nessuno stage trovato con i filtri specificati.")
    }
  }
}

// ─── stages describe ──────────────────────────────────────────────────────────

@Command(name = "describe", sortOptions = false, description = Array("Dettaglio, parametri e semantica di uno stage."))
class PipelineStagesDescribeCommand extends BaseSubCommand {

  @Parameters(index = "0", description = Array("Nome stage (es. intelligent_join, fetch, extract)"))
  private var stageName: String = ""

  override def startRun(): Unit = {
    val base = StageCatalog.resolveBase(stageName)
    StageCatalog.find(base) match {
      case None =>
        System.out.println(s"Stage '$stageName' non trovato nel catalogo.")
        System.out.println(s"Stages disponibili: ${StageCatalog.list().map(_("name")).mkString(", ")}")
      case Some(s) =>
        System.out.println(s"${ANSI_BOLD}Stage:${ANSI_RESET}       ${s.getOrElse("name", "")}")
        System.out.println(s"${ANSI_BOLD}Tipo:${ANSI_RESET}        ${s.getOrElse("extensionType", "")} | Categoria: ${s.getOrElse("category", "")}")
        System.out.println(s"${ANSI_BOLD}Descrizione:${ANSI_RESET} ${s.getOrElse("description", "")}")

        s.get("args") match {
          case Some(l: java.util.List[_]) if !l.isEmpty =>
            System.out.println(s"\n${ANSI_BOLD}Parametri (args):${ANSI_RESET}")
            val jList = l.asInstanceOf[java.util.List[java.util.Map[String, Any]]]
            for (i <- 0 until jList.size()) {
              val p = jList.get(i)
              val req = if (p.getOrDefault("required", false).toString == "true") s" ${ANSI_RED}(required)${ANSI_RESET}" else " (opzionale)"
              System.out.println(s"  [${i + 1}] ${ANSI_CYAN}${p.get("name")}${ANSI_RESET} [${p.get("type")}]$req")
              System.out.println(s"      ${p.get("description")}")
            }
          case _ =>
            System.out.println(s"\n${ANSI_BOLD}Parametri:${ANSI_RESET} nessuno (o dipendono dal subtype)")
        }

        if (s.get("supportsSubtype").exists(_.toString == "true")) {
          System.out.println(s"\n${ANSI_BOLD}Subtype:${ANSI_RESET} questo stage accetta un nome funzione come suffisso.")
          System.out.println(s"  Sintassi: stage: ${s("name")}:<nome_funzione>")
          System.out.println(s"  Esempio:  stage: ${s("name")}:price_normalizer")
        }

        // Se lo stage accetta un actionPrompt, mostra info sulle actions disponibili
        val hasActionPrompt = s.get("args").exists {
          case l: java.util.List[_] =>
            l.asInstanceOf[java.util.List[java.util.Map[String, Any]]].asScala
              .exists(p => Option(p.get("name")).exists(_.toString.toLowerCase.contains("action")))
          case _ => false
        }
        if (hasActionPrompt) {
          System.out.println(s"\n${ANSI_BOLD}Actions supportate:${ANSI_RESET} il parametro 'actionPrompt' accetta una descrizione in linguaggio naturale")
          System.out.println("  delle interazioni browser da eseguire prima della raccolta dati. Esempi:")
          System.out.println(s"""  ${ANSI_CYAN}"click cookie accept button"${ANSI_RESET}   -> click su banner cookie""")
          System.out.println(s"""  ${ANSI_CYAN}"scroll down to load more"${ANSI_RESET}     -> scroll infinito""")
          System.out.println(s"""  ${ANSI_CYAN}"type 'keyword' in search then submit"${ANSI_RESET}  -> ricerca""")
          System.out.println(s"""  ${ANSI_CYAN}"none"${ANSI_RESET}                          -> nessuna action""")
          System.out.println(s"\n  Per la lista completa: ${ANSI_BOLD}webrobot pipeline stages actions list${ANSI_RESET}")
        }

        s.get("example").foreach { ex =>
          System.out.println(s"\n${ANSI_BOLD}Esempio YAML:${ANSI_RESET}\n")
          ex.toString.linesIterator.foreach(line => System.out.println("  " + line))
        }
    }
    System.out.println()
  }
}

// ─── stages actions list ───────────────────────────────────────────────────────

@Command(name = "list", sortOptions = false, description = Array("Elenca le browser actions disponibili per stages come intelligent_join."))
class PipelineStagesActionsListCommand extends BaseSubCommand {

  @Opt(names = Array("--search"), description = Array("Ricerca nel nome e descrizione"))
  private var search: String = ""

  override def startRun(): Unit = {
    val actions = StageCatalog.listActions(if (search.nonEmpty) Some(search) else None)
    if (actions.isEmpty) {
      System.out.println("Nessuna action trovata.")
      return
    }
    val dg = new DataGrid("ACTION", "DESCRIZIONE")
    actions.foreach { a =>
      dg.add(
        a.getOrElse("name", "").toString,
        a.getOrElse("description", "").toString.take(70)
      )
    }
    dg.render
    System.out.println(s"\n${actions.size} actions disponibili. Usa 'webrobot pipeline stages actions describe <name>' per dettagli.\n")
    System.out.println(s"${ANSI_BOLD}Nota:${ANSI_RESET} le actions vengono descritte in linguaggio naturale nel parametro actionPrompt.")
    System.out.println(s"""Esempio per intelligent_join: args: ["auto", "click cookie button then scroll down", 20]\n""")
  }
}

// ─── stages actions describe ──────────────────────────────────────────────────

@Command(name = "describe", sortOptions = false, description = Array("Dettaglio di una browser action."))
class PipelineStagesActionsDescribeCommand extends BaseSubCommand {

  @Parameters(index = "0", description = Array("Nome action (es. click, type, scroll)"))
  private var actionName: String = ""

  override def startRun(): Unit = {
    StageCatalog.findAction(actionName) match {
      case None =>
        System.out.println(s"Action '$actionName' non trovata.")
        System.out.println(s"Actions disponibili: ${StageCatalog.listActions().map(_("name")).mkString(", ")}")
      case Some(a) =>
        System.out.println(s"${ANSI_BOLD}Action:${ANSI_RESET}      ${a.getOrElse("name", "")}")
        System.out.println(s"${ANSI_BOLD}Descrizione:${ANSI_RESET} ${a.getOrElse("description", "")}")
        a.get("usage").foreach { u =>
          System.out.println(s"${ANSI_BOLD}Uso tipico:${ANSI_RESET}  ${ANSI_CYAN}${u}${ANSI_RESET}")
        }
        a.get("params") match {
          case Some(l: java.util.List[_]) if !l.isEmpty =>
            System.out.println(s"\n${ANSI_BOLD}Parametri (nella frase naturale):${ANSI_RESET}")
            val jList = l.asInstanceOf[java.util.List[java.util.Map[String, Any]]]
            for (i <- 0 until jList.size()) {
              val p = jList.get(i)
              System.out.println(s"  ${ANSI_CYAN}${p.get("name")}${ANSI_RESET} [${p.get("type")}] — ${p.get("description")}")
            }
          case _ =>
        }
        a.get("examples").foreach {
          case l: java.util.List[_] if !l.isEmpty =>
            System.out.println(s"\n${ANSI_BOLD}Esempi di actionPrompt:${ANSI_RESET}")
            l.asInstanceOf[java.util.List[_]].asScala.foreach(ex => System.out.println(s"""  ${ANSI_CYAN}"$ex"${ANSI_RESET}"""))
          case _ =>
        }
    }
    System.out.println()
  }
}

// ─── stages refresh ───────────────────────────────────────────────────────────

@Command(name = "refresh", sortOptions = false, description = Array(
  "Aggiorna il catalogo stage dal server (GET /webrobot/api/catalog/stages) e invalida la cache locale.",
  "Il catalogo remoto include gli stage di tutti i plugin installati e abilitati."
))
class PipelineStagesRefreshCommand extends BaseSubCommand {

  @Opt(names = Array("--invalidate-only"), description = Array("Invalida solo la cache senza scaricare il nuovo catalogo"))
  private var invalidateOnly: Boolean = false

  override def startRun(): Unit = {
    this.init()
    StageCatalog.invalidateCache()
    if (invalidateOnly) {
      System.out.println(s"${ANSI_GREEN}Cache invalidata.${ANSI_RESET}")
      return
    }
    System.out.print(s"${ANSI_CYAN}Aggiornamento catalogo stage...${ANSI_RESET} ")
    System.out.flush()
    val ok = StageCatalog.fetchRemote(apiClient())
    if (ok) {
      val count = StageCatalog.list().size
      System.out.println(s"${ANSI_GREEN}OK${ANSI_RESET} — $count stage nel catalogo.")
    } else {
      System.out.println(s"${ANSI_YELLOW}Fetch remoto non riuscito — uso il catalogo locale.${ANSI_RESET}")
      System.out.println("Verifica che auth e api_endpoint siano configurati in ~/.webrobot/config.conf")
    }
  }
}

// ─── stages actions (group) ───────────────────────────────────────────────────

@Command(
  name = "actions",
  mixinStandardHelpOptions = true,
  sortOptions = false,
  description = Array(
    "Browser automation actions usabili come actionPrompt negli stage browser (intelligent_join, visitJoin, ecc.).",
    "Le actions vengono descritte in linguaggio naturale — l'engine le interpreta e le traduce in interazioni reali."
  ),
  subcommands = Array(classOf[PipelineStagesActionsListCommand], classOf[PipelineStagesActionsDescribeCommand])
)
class PipelineStagesActionsCommand extends Runnable {
  def run(): Unit = System.err.println("Uso: webrobot pipeline stages actions <list|describe>")
}

// ─── stages (group) ───────────────────────────────────────────────────────────

@Command(
  name = "stages",
  mixinStandardHelpOptions = true,
  sortOptions = false,
  description = Array("Catalogo stage e browser actions della piattaforma."),
  subcommands = Array(
    classOf[PipelineStagesListCommand],
    classOf[PipelineStagesDescribeCommand],
    classOf[PipelineStagesRefreshCommand],
    classOf[PipelineStagesActionsCommand]
  )
)
class PipelineStagesCommand extends Runnable {
  def run(): Unit = System.err.println("Uso: webrobot pipeline stages <list|describe|refresh|actions>")
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

  @Opt(names = Array("--param"), description = Array("Parametro chiave=valore → inserito in config: (ripetibile)"), arity = "0..*")
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
    val base = StageCatalog.resolveBase(stageName)
    if (!StageCatalog.exists(base)) {
      System.out.println(s"${ANSI_YELLOW}WARN: Stage '$base' non trovato nel catalogo locale.${ANSI_RESET}")
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
    val idx = stagesAfter - 1
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

@Command(name = "show", sortOptions = false, description = Array("Mostra la pipeline corrente dal file YAML (stages, input, output, schedule)."))
class PipelineShowCommand extends BaseSubCommand {

  @Opt(names = Array("-f", "--file"), description = Array("File manifest YAML"), required = true)
  private var file: File = _

  override def startRun(): Unit = {
    if (!file.exists()) { System.out.println(s"File non trovato: ${file.getPath}"); return }
    val docs = YamlManifest.load(file)
    YamlManifest.findPipeline(docs) match {
      case None => System.out.println("Nessuna sezione Pipeline trovata nel file.")
      case Some(pd) =>
        val meta = pd.get("metadata").collect { case m: scala.collection.mutable.Map[_,_] => m.asInstanceOf[scala.collection.mutable.Map[String,Any]] }
        val spec = pd.get("spec").collect { case m: scala.collection.mutable.Map[_,_] => m.asInstanceOf[scala.collection.mutable.Map[String,Any]] }

        val pName = meta.flatMap(_.get("name")).map(_.toString).getOrElse("(senza nome)")
        System.out.println(s"${ANSI_BOLD}Pipeline:${ANSI_RESET} $pName")
        meta.flatMap(_.get("description")).foreach(d => System.out.println(s"  $d"))
        spec.flatMap(_.get("project")).foreach(p => System.out.println(s"  Progetto: $p"))

        // Input
        spec.flatMap(_.get("input")).foreach {
          case m: scala.collection.mutable.Map[_,_] =>
            val im = m.asInstanceOf[scala.collection.mutable.Map[String,Any]]
            System.out.println(s"\n${ANSI_BOLD}Input:${ANSI_RESET}")
            im.get("dataset").foreach(d => System.out.println(s"  dataset: $d"))
            im.get("path").foreach(p => System.out.println(s"  path:    $p"))
          case other => System.out.println(s"\n${ANSI_BOLD}Input:${ANSI_RESET} $other")
        }

        // Stages
        val stages = YamlManifest.stageList(pd)
        System.out.println(s"\n${ANSI_BOLD}Stages (${stages.size()}):${ANSI_RESET}")
        if (stages.isEmpty) {
          System.out.println("  (nessuno — usa 'webrobot pipeline add-stage <name> -f pipeline.yaml')")
        } else {
          (0 until stages.size()).foreach { i =>
            stages.get(i) match {
              case m: java.util.Map[_, _] =>
                val sm = m.asInstanceOf[java.util.Map[String, Any]]
                val name = Option(sm.get("name")).map(n => s""" ${ANSI_CYAN}"$n"${ANSI_RESET}""").getOrElse("")
                val args = Option(sm.get("args")).map(a => s" ${ANSI_YELLOW}args=$a${ANSI_RESET}").getOrElse("")
                val stage = Option(sm.get("stage")).map(_.toString).getOrElse("?")
                val inCatalog = StageCatalog.exists(StageCatalog.resolveBase(stage))
                val mark = if (inCatalog) s"${ANSI_GREEN}✓${ANSI_RESET}" else s"${ANSI_YELLOW}?${ANSI_RESET}"
                System.out.println(s"  $mark [$i] $stage$name$args")
              case other => System.out.println(s"    [$i] $other")
            }
          }
        }

        // Output
        spec.flatMap(_.get("output")).foreach {
          case m: scala.collection.mutable.Map[_,_] =>
            val om = m.asInstanceOf[scala.collection.mutable.Map[String,Any]]
            System.out.println(s"\n${ANSI_BOLD}Output:${ANSI_RESET}")
            om.get("format").foreach(f => System.out.println(s"  format: $f"))
            om.get("mode").foreach(m => System.out.println(s"  mode:   $m"))
            om.get("path").foreach(p => System.out.println(s"  path:   $p"))
          case other => System.out.println(s"\n${ANSI_BOLD}Output:${ANSI_RESET} $other")
        }

        // Schedule
        spec.flatMap(_.get("schedule")).foreach {
          case m: scala.collection.mutable.Map[_,_] =>
            val sm = m.asInstanceOf[scala.collection.mutable.Map[String,Any]]
            val cron = sm.getOrElse("cron", "?").toString
            val tz   = sm.getOrElse("timezone", "Europe/Rome").toString
            System.out.println(s"\n${ANSI_BOLD}Schedule:${ANSI_RESET} $cron ($tz)")
          case other => System.out.println(s"\n${ANSI_BOLD}Schedule:${ANSI_RESET} $other")
        }

        System.out.println()
        System.out.println(s"  ${ANSI_CYAN}Applica:${ANSI_RESET} webrobot pipeline apply -f ${file.getName}")
        System.out.println(s"  ${ANSI_CYAN}Esegui: ${ANSI_RESET} webrobot pipeline run -f ${file.getName} --follow")
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

    if (!dryRun && node != null) {
      val projectId = findJsonField(node, "projectId")
      val jobIdRaw  = findJsonField(node, "jobId")
      val jobId     = if (jobIdRaw != null) jobIdRaw else findJsonField(node, "id")
      if (projectId != null && jobId != null) {
        System.out.println(s"\n${ANSI_BOLD}Prossimi passi:${ANSI_RESET}")
        System.out.println(s"  ${ANSI_CYAN}webrobot job execute -p $projectId -j $jobId --follow${ANSI_RESET}")
        System.out.println(s"  ${ANSI_CYAN}webrobot pipeline run -f ${file.getName} --follow${ANSI_RESET}")
      }
    }
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
                  System.out.println(s"  ${ANSI_YELLOW}?${ANSI_RESET} Stage '$sName' non nel catalogo (potrebbe essere un plugin custom)")
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

// ─── pipeline run ─────────────────────────────────────────────────────────────

@Command(
  name = "run",
  sortOptions = false,
  description = Array("Applica il manifest e avvia immediatamente l'esecuzione del job risultante.")
)
class PipelineRunCommand extends BaseSubCommand {

  @Opt(names = Array("-f", "--file"), description = Array("File manifest YAML"), required = true)
  private var file: File = _

  @Opt(names = Array("-y", "--yes"), description = Array("Salta conferma interattiva"))
  private var yes: Boolean = false

  @Opt(names = Array("-F", "--follow"), description = Array("Polling dello stato ogni 5s fino al completamento"))
  private var follow: Boolean = false

  @Opt(names = Array("--camoufox-mode"), description = Array("Modalità Camoufox: IN_CLUSTER (cluster esistente, no VM) | EXTERNAL_VM (Ansible+Hetzner, default utenti)"))
  private var camoufoxMode: String = ""

  override def startRun(): Unit = {
    this.init()
    if (!file.exists()) { System.out.println(s"File non trovato: ${file.getPath}"); return }
    val yamlContent = new String(Files.readAllBytes(file.toPath), "UTF-8")

    if (!yes) {
      System.out.print(s"Applicare e avviare '${file.getName}'? [y/N] ")
      val answer = scala.io.StdIn.readLine()
      if (answer == null || !answer.trim.equalsIgnoreCase("y")) {
        System.out.println("Annullato.")
        return
      }
    }

    System.out.println(s"${ANSI_CYAN}[1/2] Applicazione manifest...${ANSI_RESET}")
    val body = new java.util.HashMap[String, String]()
    body.put("yaml", yamlContent)
    val applyNode = OpenApiHttp.postJson(apiClient(), "/webrobot/api/manifest/apply", body)

    val projectId = findJsonField(applyNode, "projectId")
    val jobId     = findJsonField(applyNode, "jobId")

    if (projectId == null || jobId == null) {
      System.out.println(s"${ANSI_YELLOW}Manifest applicato ma projectId/jobId non estratti dalla risposta.${ANSI_RESET}")
      JsonCliUtil.printJson(applyNode)
      System.out.println(s"Avvia manualmente: ${ANSI_CYAN}webrobot job execute -p <projectId> -j <jobId> --follow${ANSI_RESET}")
      return
    }

    System.out.println(s"  progetto: $projectId  job: $jobId")
    System.out.println(s"${ANSI_CYAN}[2/2] Avvio esecuzione...${ANSI_RESET}")

    val execPath =
      "/webrobot/api/projects/id/" + apiClient().escapeString(projectId) +
      "/jobs/" + apiClient().escapeString(jobId) + "/execute"
    val execBody = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode()
    if (camoufoxMode != null && camoufoxMode.trim.nonEmpty)
      execBody.put("camoufoxMode", camoufoxMode.trim.toUpperCase)
    val execNode = OpenApiHttp.postJson(apiClient(), execPath, execBody)

    val execId = extractJsonField(execNode, "executionId", "id", "execution_id", "executionReferenceId")
    if (execId == null || execId.isEmpty) {
      System.out.println(s"${ANSI_YELLOW}Esecuzione avviata ma executionId non trovato nella risposta.${ANSI_RESET}")
      JsonCliUtil.printJson(execNode)
      return
    }

    System.out.println(s"${ANSI_GREEN}Esecuzione avviata — executionId: $execId${ANSI_RESET}")
    if (follow) {
      followExecution(projectId, jobId, execId)
    } else {
      System.out.println(s"\n  ${ANSI_CYAN}Per seguire lo stato:${ANSI_RESET}")
      System.out.println(s"  webrobot pipeline run -f ${file.getName} --follow")
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
    "  webrobot pipeline stages describe intelligent_join",
    "  webrobot pipeline stages actions list",
    "  webrobot pipeline add-stage fetch --args \"https://example.com\" -f pipeline.yaml",
    "  webrobot pipeline add-stage intelligent_join --args \"auto,click cookie button,20\" -f pipeline.yaml",
    "  webrobot pipeline set-output --format parquet -f pipeline.yaml",
    "  webrobot pipeline show -f pipeline.yaml",
    "  webrobot pipeline run -f pipeline.yaml --follow",
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
    classOf[PipelineRunCommand],
    classOf[PipelineStagesCommand]
  )
)
class RunPipelineCommand extends Runnable {
  def run(): Unit = System.err.println(
    "Uso: webrobot pipeline <sottocomando>\n" +
    "Sottocomandi: set | add-stage | remove-stage | set-input | set-output | set-schedule | show | apply | validate | run | stages"
  )
}
