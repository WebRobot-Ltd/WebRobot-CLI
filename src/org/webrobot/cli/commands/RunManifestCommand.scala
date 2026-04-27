package org.webrobot.cli.commands

import org.webrobot.cli.manifest.YamlManifest
import org.webrobot.cli.openapi.{JsonCliUtil, OpenApiHttp}
import picocli.CommandLine.{Command, Option => Opt, Parameters}

import java.io.{File, FileWriter}
import java.nio.file.Files

// ─── manifest apply ───────────────────────────────────────────────────────────

@Command(
  name = "apply",
  sortOptions = false,
  description = Array(
    "Applica uno o più manifest YAML al server.",
    "Supporta file multi-documento (separati da ---) con kind: Project|JobCategory|Job|Pipeline|Dataset|CloudCredential|Schedule."
  )
)
class ManifestApplyCommand extends BaseSubCommand {

  @Opt(names = Array("-f", "--file"), description = Array("File manifest YAML (supporta glob *.yaml)"), required = true)
  private var filePattern: String = ""

  @Opt(names = Array("--dry-run"), description = Array("Valida senza applicare"))
  private var dryRun: Boolean = false

  @Opt(names = Array("-y", "--yes"), description = Array("Salta conferma interattiva"))
  private var yes: Boolean = false

  override def startRun(): Unit = {
    this.init()
    val files = resolveFiles(filePattern)
    if (files.isEmpty) { System.out.println(s"Nessun file trovato: $filePattern"); return }

    files.foreach { f =>
      if (!f.exists()) { System.out.println(s"File non trovato: ${f.getPath}"); return }
      val yamlContent = new String(Files.readAllBytes(f.toPath), "UTF-8")

      if (!yes && !dryRun) {
        System.out.print(s"Applicare '${f.getName}'? [y/N] ")
        val answer = scala.io.StdIn.readLine()
        if (answer == null || !answer.trim.equalsIgnoreCase("y")) {
          System.out.println("Annullato.")
          return
        }
      }

      val endpoint = if (dryRun) "/webrobot/api/manifest/validate" else "/webrobot/api/manifest/apply"
      val body = new java.util.HashMap[String, String]()
      body.put("yaml", yamlContent)

      try {
        val node = OpenApiHttp.postJson(apiClient(), endpoint, body)
        if (dryRun) System.out.println(s"[DRY-RUN] ${f.getName}:")
        else System.out.println(s"Applied ${f.getName}:")
        JsonCliUtil.printJson(node)
      } catch {
        case e: Exception =>
          System.out.println(s"${ANSI_RED}Errore su ${f.getName}: ${e.getMessage}${ANSI_RESET}")
      }
    }
  }

  private def resolveFiles(pattern: String): List[File] = {
    val f = new File(pattern)
    if (f.exists()) return List(f)
    // glob semplice nella directory corrente
    val dir = Option(f.getParentFile).getOrElse(new File("."))
    val namePattern = f.getName.replace("*", ".*").replace("?", ".")
    val matched = dir.listFiles(new java.io.FilenameFilter {
      override def accept(d: File, n: String): Boolean = n.matches(namePattern)
    })
    if (matched == null || matched.isEmpty) List(f) // restituisce il file anche se non esiste (errore gestito sopra)
    else matched.toList.sortBy(_.getName)
  }
}

// ─── manifest validate ────────────────────────────────────────────────────────

@Command(name = "validate", sortOptions = false, description = Array("Valida uno o più manifest YAML senza applicarli."))
class ManifestValidateCommand extends BaseSubCommand {

  @Opt(names = Array("-f", "--file"), description = Array("File manifest YAML"), required = true)
  private var file: File = _

  override def startRun(): Unit = {
    this.init()
    if (!file.exists()) { System.out.println(s"File non trovato: ${file.getPath}"); return }
    val yamlContent = new String(Files.readAllBytes(file.toPath), "UTF-8")
    val body = new java.util.HashMap[String, String]()
    body.put("yaml", yamlContent)
    try {
      val node = OpenApiHttp.postJson(apiClient(), "/webrobot/api/manifest/validate", body)
      JsonCliUtil.printJson(node)
    } catch {
      case _: Exception => System.out.println("(endpoint di validazione remota non disponibile)")
    }
  }
}

// ─── manifest get ─────────────────────────────────────────────────────────────

@Command(name = "get", sortOptions = false, description = Array("Esporta una risorsa remota in formato manifest YAML."))
class ManifestGetCommand extends BaseSubCommand {

  @Parameters(index = "0", description = Array("Tipo risorsa: Pipeline|Project|Dataset|CloudCredential"))
  private var kind: String = ""

  @Parameters(index = "1", description = Array("ID o nome della risorsa"))
  private var nameOrId: String = ""

  @Opt(names = Array("-o", "--output"), description = Array("File di output (default: stdout)"))
  private var outputFile: String = ""

  override def startRun(): Unit = {
    this.init()
    val path = s"/webrobot/api/manifest/export?kind=${apiClient().escapeString(kind)}&id=${apiClient().escapeString(nameOrId)}"
    val node = OpenApiHttp.getJson(apiClient(), path)
    val yaml = if (node != null) node.toPrettyString else "(nessun risultato)"
    if (outputFile.nonEmpty) {
      val fw = new FileWriter(new File(outputFile))
      try fw.write(yaml)
      finally fw.close()
      System.out.println(s"Esportato in $outputFile")
    } else {
      System.out.println(yaml)
    }
  }
}

// ─── manifest init ────────────────────────────────────────────────────────────

@Command(name = "init", sortOptions = false, description = Array("Crea un manifest YAML di esempio pronto da editare."))
class ManifestInitCommand extends BaseSubCommand {

  @Opt(names = Array("-o", "--output"), description = Array("File di output (default: webrobot-manifest.yaml)"))
  private var outputFile: String = "webrobot-manifest.yaml"

  @Opt(names = Array("--template"), description = Array("Template: blank|pipeline|full (default: blank)"))
  private var template: String = "blank"

  override def startRun(): Unit = {
    val content = template match {
      case "pipeline" => pipelineTemplate()
      case "full"     => fullTemplate()
      case _          => blankTemplate()
    }
    val fw = new FileWriter(new File(outputFile))
    try fw.write(content)
    finally fw.close()
    System.out.println(s"Manifest creato: $outputFile")
    System.out.println("Prossimo: webrobot pipeline add-stage fetch --args 'https://example.com' -f " + outputFile)
  }

  private def blankTemplate() =
    """---
apiVersion: webrobot/v1
kind: Pipeline
metadata:
  name: my-pipeline
  # project: my-project-id
spec:
  pipeline: []
  output:
    format: parquet
    mode: overwrite
"""

  private def pipelineTemplate() =
    """---
apiVersion: webrobot/v1
kind: Pipeline
metadata:
  name: my-pipeline
  project: my-project-id
spec:
  input:
    dataset: my-dataset-id

  pipeline:
    - stage: fetch
      args: ["https://example.com"]
    - stage: intelligent_flatSelect
      args: ["product cards", "extract name and price", "prod_"]

  output:
    format: parquet
    mode: overwrite

  schedule:
    cron: "0 6 * * *"
    timezone: "Europe/Rome"
"""

  private def fullTemplate() =
    """---
apiVersion: webrobot/v1
kind: Project
metadata:
  name: my-project
spec:
  description: "Il mio progetto"

---

apiVersion: webrobot/v1
kind: JobCategory
metadata:
  name: my-category
spec:
  name: "My Category"

---

apiVersion: webrobot/v1
kind: Dataset
metadata:
  name: my-input-dataset
spec:
  format: CSV
  datasetType: INPUT
  # source:
  #   localFile: ./data/input.csv

---

apiVersion: webrobot/v1
kind: CloudCredential
metadata:
  name: my-api-key
spec:
  provider: generic
  apiKey: "${MY_API_KEY}"

---

apiVersion: webrobot/v1
kind: Pipeline
metadata:
  name: my-pipeline
  project: ref:my-project
spec:
  input:
    dataset: ref:my-input-dataset

  pipeline:
    - stage: fetch
      args: ["https://example.com"]
    - stage: extract
      args:
        - { selector: "h1", method: "text", as: "title" }

  output:
    format: parquet
    mode: overwrite

  cloudCredentials:
    - ref:my-api-key
"""
}

// ─── manifest (group) ─────────────────────────────────────────────────────────

@Command(
  name = "manifest",
  mixinStandardHelpOptions = true,
  sortOptions = false,
  description = Array("Gestione declarativa delle risorse WebRobot via manifest YAML (kubectl-style)."),
  footer = Array(
    "",
    "Risorse supportate: Project | JobCategory | Job | Pipeline | Dataset | CloudCredential | Schedule",
    "",
    "Esempi:",
    "  webrobot manifest init -o infra.yaml",
    "  webrobot manifest apply -f infra.yaml",
    "  webrobot manifest validate -f infra.yaml",
    "  webrobot manifest get Pipeline my-pipeline",
    ""
  ),
  subcommands = Array(
    classOf[ManifestApplyCommand],
    classOf[ManifestValidateCommand],
    classOf[ManifestGetCommand],
    classOf[ManifestInitCommand]
  )
)
class RunManifestCommand extends Runnable {
  def run(): Unit = System.err.println("Uso: webrobot manifest <apply|validate|get|init>")
}
