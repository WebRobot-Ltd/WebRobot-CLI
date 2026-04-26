package org.webrobot.cli.commands

import java.io.FileInputStream
import java.net.URL

import WebRobot.Cli.Sdk.Utils.Utils
import org.webrobot.cli.openapi.{JsonCliUtil, OpenApiHttp}
import picocli.CommandLine.{Command, Option}

@Command(
  name = "exportproject",
  sortOptions = false,
  description = Array("Export progetto (GET /webrobot/api/package/export/id/{projectId}), download zip."),
  footer = Array()
)
class RunExportProjectCommand extends BaseSubCommand {

  @Option(names = Array("-f", "--folder"), description = Array("percorso file zip di destinazione"), required = true)
  private var zipPackageFolder: String = ""

  @Option(names = Array("-p", "--projectId"), description = Array("project id"), required = true)
  private var projectId: String = ""

  override def startRun(): Unit = {
    this.init()
    val path = "/webrobot/api/package/export/id/" + apiClient().escapeString(projectId)
    val node = OpenApiHttp.getJson(apiClient(), path)
    val url = JsonCliUtil
      .extractDownloadUrl(node)
      .getOrElse(throw new IllegalStateException("Nessun URL nella risposta export: " + node))
    Utils.downloadFile(url, zipPackageFolder)
  }
}

@Command(
  name = "exportall",
  sortOptions = false,
  description = Array("Export tutti i progetti (GET /webrobot/api/package/export/all), download zip."),
  footer = Array()
)
class RunExportAllCommand extends BaseSubCommand {

  @Option(names = Array("-f", "--folder"), description = Array("percorso file zip di destinazione"), required = true)
  private var zipPackageFolder: String = ""

  override def startRun(): Unit = {
    this.init()
    val node = OpenApiHttp.getJson(apiClient(), "/webrobot/api/package/export/all")
    val url = JsonCliUtil
      .extractDownloadUrl(node)
      .getOrElse(throw new IllegalStateException("Nessun URL nella risposta export: " + node))
    Utils.downloadFile(url, zipPackageFolder)
  }
}

@Command(
  name = "importproject",
  sortOptions = false,
  description = Array(
    "Import progetto: presigned upload (GET /webrobot/api/package/upload), PUT zip, poi GET /webrobot/api/package/import/id/{projectId}."
  ),
  footer = Array()
)
class RunImportProject extends BaseSubCommand {

  @Option(names = Array("-z", "--zip"), description = Array("file zip sorgente"), required = true)
  private var zipPackageFile: String = ""

  @Option(names = Array("-p", "--projectId"), description = Array("project id"), required = true)
  private var projectId: String = ""

  override def startRun(): Unit = {
    this.init()
    val uploadNode = OpenApiHttp.getJson(apiClient(), "/webrobot/api/package/upload")
    val s3Url = JsonCliUtil
      .extractDownloadUrl(uploadNode)
      .getOrElse(throw new IllegalStateException("Nessun URL upload nella risposta: " + uploadNode))
    val in = new FileInputStream(zipPackageFile)
    try uploadFile(new URL(s3Url), in)
    finally in.close()
    val importPath = "/webrobot/api/package/import/id/" + apiClient().escapeString(projectId)
    OpenApiHttp.getJson(apiClient(), importPath)
  }
}

@Command(
  name = "importall",
  sortOptions = false,
  description = Array(
    "Import tutti: presigned upload (GET /webrobot/api/package/upload), PUT zip, poi GET /webrobot/api/package/import/all."
  ),
  footer = Array()
)
class RunImportAllCommand extends BaseSubCommand {

  @Option(names = Array("-z", "--zip"), description = Array("file zip sorgente"), required = true)
  private var zipPackageFile: String = ""

  override def startRun(): Unit = {
    this.init()
    val uploadNode = OpenApiHttp.getJson(apiClient(), "/webrobot/api/package/upload")
    val s3Url = JsonCliUtil
      .extractDownloadUrl(uploadNode)
      .getOrElse(throw new IllegalStateException("Nessun URL upload nella risposta: " + uploadNode))
    val in = new FileInputStream(zipPackageFile)
    try uploadFile(new URL(s3Url), in)
    finally in.close()
    OpenApiHttp.getJson(apiClient(), "/webrobot/api/package/import/all")
  }
}

@Command(
  name = "package",
  sortOptions = false,
  description = Array("Import/export configurazioni (REST /webrobot/api/package/...)."),
  footer = Array(),
  subcommands = Array(
    classOf[RunImportAllCommand],
    classOf[RunExportAllCommand],
    classOf[RunExportProjectCommand],
    classOf[RunImportProject]
  )
)
class RunImportExportCommand extends Runnable {

  def run(): Unit = {
    System.err.println(
      "Uso: webrobot package <sottocomando>. Sottocomandi: importall | exportall | importproject | exportproject"
    )
    System.err.println("Esempio: webrobot package exportall -f /tmp/export.zip")
  }
}
