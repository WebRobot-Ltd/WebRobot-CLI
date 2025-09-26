package org.webrobot.cli.commands
import java.io.FileInputStream
import java.net.URL

import WebRobot.Cli.Sdk.Utils.Utils
import WebRobot.Cli.Sdk.model.{DeleteDatasetFromIdRequest, Export_allRequest, Export_projectRequest, Get_url_uploadRequest, Get_url_uploadResult, Import_allRequest, Import_allResult, Import_projectRequest, Upload_fileRequest, Upload_fileResult}
import picocli.CommandLine.{Command, Option}

@Command(name = "exportproject", sortOptions = false,
  description = Array(
    "export project"),
  footer = Array()
)
class RunExportProjectCommand extends BaseSubCommand {

  @Option(names = Array("-f", "--folder"), description = Array("set the zip"),required = true) private var zipPackageFolder : String = ""
  @Option(names = Array("-p", "--projectId"), description = Array("set the projectId"),required = true) private var projectId : String = ""

  override def startRun(): Unit = {
    this.init()
    var exportProjectRequest : Export_projectRequest  = new Export_projectRequest();
    exportProjectRequest.setProjectId(projectId)
    var exportResult = sdkClient.export_project(exportProjectRequest)
    var url =  exportResult.getStringResult.getResult
    Utils.downloadFile(url,zipPackageFolder)
  }
}


@Command(name = "exportall", sortOptions = false,
  description = Array(
    "export all projects"),
  footer = Array()
)
class RunExportAllCommand extends BaseSubCommand {

  @Option(names = Array("-f", "--folder"), description = Array("set the zip"),required = true) private var zipPackageFolder : String = ""

  override def startRun(): Unit = {
    this.init()
    var exportAllRequest : Export_allRequest  = new Export_allRequest();
    var exportResult = sdkClient.export_all(exportAllRequest)
    var url =  exportResult.getStringResult.getResult
    Utils.downloadFile(url,zipPackageFolder)
  }
}


@Command(name = "importproject", sortOptions = false,
  description = Array(
    "import project"),
  footer = Array()
)
class RunImportProject extends BaseSubCommand {

  @Option(names = Array("-z", "--zip"), description = Array("set the zip"),required = true) private var zipPackageFile : String = ""
  @Option(names = Array("-p", "--projectId"), description = Array("set the projectId"),required = true) private var projectId : String = ""

  override def startRun(): Unit = {

    this.init()
    var get_url_uploadRequest  = new Get_url_uploadRequest().sdkRequestConfig(this.getCustomSdkRequestConfig())
    var result : Get_url_uploadResult = sdkClient.get_url_upload(get_url_uploadRequest)
    var s3Url = result.getStringResult.getResult
    var in : FileInputStream = null
    import java.io.FileInputStream
    in = new FileInputStream(zipPackageFile)
    Utils.uploadFile(new URL(s3Url),in)
    var importProjectRequest = new  Import_projectRequest().sdkRequestConfig(this.getCustomSdkRequestConfig())
    importProjectRequest.setProjectId(projectId)
    sdkClient.import_project(importProjectRequest)
  }
}

@Command(name = "importall", sortOptions = false,
  description = Array(
    "import all projects"),
  footer = Array()
)
class RunImportAllCommand extends BaseSubCommand {

  @Option(names = Array("-z", "--zip"), description = Array("set the zip"),required = true) private var zipPackageFile : String = ""

  override def startRun(): Unit = {

    this.init()
    var get_url_uploadRequest  = new Get_url_uploadRequest().sdkRequestConfig(this.getCustomSdkRequestConfig())
    var result : Get_url_uploadResult = sdkClient.get_url_upload(get_url_uploadRequest)
    var s3Url = result.getStringResult.getResult
    var in : FileInputStream = null
    import java.io.FileInputStream
    in = new FileInputStream(zipPackageFile)
    Utils.uploadFile(new URL(s3Url),in)
    var import_allRequest = new Import_allRequest().sdkRequestConfig(this.getCustomSdkRequestConfig())
    sdkClient.import_all(import_allRequest)
  }
}

@Command(name = "package", sortOptions = false,
  description = Array(
    "Manage Import and Export of data scraping configuration"),
  footer = Array(),
  subcommands = Array(classOf[RunImportAllCommand],classOf[RunExportAllCommand],classOf[RunExportProjectCommand],classOf[RunImportProject])
)
class RunImportExportCommand   extends Runnable  {

  def run(): Unit = {

    println("Run ImportExport Command: importall|exportall|importproject|exportproject")
  }
}