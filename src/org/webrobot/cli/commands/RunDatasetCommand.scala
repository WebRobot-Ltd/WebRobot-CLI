package org.webrobot.cli.commands
import java.io.{File, FileInputStream, IOException, InputStream}
import java.net.URL
import java.nio.file.Path
import java.time.DateTimeException

import picocli.CommandLine.{Command, Option}
import WebRobot.Cli.Sdk.model.{Add_datasetRequest, Add_datasetResult, Create_projectRequest, Dataset, DatasetRecord, DeleteDatasetFromIdRequest, Delete_projectRequest, Get_all_datasetsRequest, Get_all_datasetsResult, Get_all_projectsRequest, Get_all_projectsResult, Get_datasetRequest, Get_datasetResult, Get_dataset_input_fileRequest, Get_dataset_input_fileResult, Get_dataset_input_file_paginationRequest, Get_dataset_versionRequest, Get_dataset_versionResult, Project, TimePeriod, Update_projectRequest, Upload_fileRequest}
import com.amazonaws.opensdk.config.{ConnectionConfiguration, TimeoutConfiguration}
import org.webrobot.cli.RunWebRobotCli
import org.webrobot.cli.utils.DataGrid
import java.nio.file.Path
import java.nio.file.Paths

import WebRobot.Cli.Sdk.Utils.Utils


import scala.collection.JavaConverters._
import scala.collection.mutable.ArrayBuffer


@Command(name = "getinputpagination", sortOptions = false,
  description = Array(
    "get input pagination"),
  footer = Array()
)
class RunGetInputPaginationDatasetCommand extends BaseSubCommand {

  @Option(names = Array("-i", "--projectId"), description = Array("set the project id"),required = true) private var projectId : String = ""
  @Option(names = Array("-bId", "--botId"), description = Array("set the botId"),required = true) private var botId : String = ""
  @Option(names = Array("-dId", "--datasetId"), description = Array("set the datasetId"),required = true) private var datasetId : String = ""
  @Option(names = Array("-l", "--limit"), description = Array("limit"),required = false) private var limit : String = "10"
  @Option(names = Array("-o", "--offset"), description = Array("offset"),required = false) private var offset : String = "0"
  override def startRun(): Unit =
  {
    try {
    this.init()
    var datasetInputFilePaginationRequest : Get_dataset_input_file_paginationRequest = new Get_dataset_input_file_paginationRequest().sdkRequestConfig(this.getCustomSdkRequestConfig())
    datasetInputFilePaginationRequest.setProjectId(projectId)
    datasetInputFilePaginationRequest.setBotId(botId)
    datasetInputFilePaginationRequest.setDatasetId(datasetId)
      datasetInputFilePaginationRequest.setOffset(offset)
      datasetInputFilePaginationRequest.setLimit(limit)
    var result =  sdkClient.get_dataset_input_file_pagination(datasetInputFilePaginationRequest)
      var records : Seq[DatasetRecord] = result.getListRecords.getRecords.asScala
      var columns = new ArrayBuffer[String]()
      if(records.size > 0)
      {
        records(0).getFields.asScala.foreach(field => columns.append(field.getName))
      }
      this.dataGrid = new DataGrid(columns : _*)
      records.foreach(item => {
        var values = new ArrayBuffer[String]()
        item.getFields.asScala.foreach(field =>   values.append(field.getValue))

        this.dataGrid.add(values : _*)
      }
      )
      if (this.dataGrid.size > 0) {
        this.dataGrid.render
        System.out.println(this.dataGrid.size + " rows in set\n")
      }
      else System.out.println("Empty set\n")
    }
    catch
      {
        case e: IOException => println("An error occurred while fetch the records")
      }
  }
}


@Command(name = "getinput", sortOptions = false,
  description = Array(
    "get input"),
  footer = Array()
)
class RunGetInputDatasetCommand extends BaseSubCommand {

  @Option(names = Array("-i", "--projectId"), description = Array("set the project id"),required = true) private var projectId : String = ""
  @Option(names = Array("-bId", "--botId"), description = Array("set the botId"),required = true) private var botId : String = ""
  @Option(names = Array("-dId", "--datasetId"), description = Array("set the datasetId"),required = true) private var datasetId : String = ""
  @Option(names = Array("-f", "--filePath"), description = Array("file name"),required = true) private var filePath : String = ""
  override def startRun(): Unit =
  {
    this.init()
    var datasetInputFileRequest : Get_dataset_input_fileRequest = new Get_dataset_input_fileRequest().sdkRequestConfig(this.getCustomSdkRequestConfig())
    datasetInputFileRequest.setProjectId(projectId)
    datasetInputFileRequest.setBotId(botId)
    datasetInputFileRequest.setDatasetId(datasetId)
    var result =  sdkClient.get_dataset_input_file(datasetInputFileRequest)
    var s3Url = result.getStringResult.getResult
    val theDir = new File(filePath)
    if (!theDir.exists) theDir.mkdirs
    Utils.downloadFile(s3Url,this.filePath)
  }
}
@Command(name = "delete", sortOptions = false,
  description = Array(
    "delete dataset"),
  footer = Array()
)
class RunDeleteDatasetCommand extends BaseSubCommand {

  @Option(names = Array("-i", "--projectId"), description = Array("set the project id"),required = true) private var projectId : String = ""
  @Option(names = Array("-bId", "--botId"), description = Array("set the botId"),required = true) private var botId : String = ""
  @Option(names = Array("-dId", "--datasetId"), description = Array("set the datasetId"),required = true) private var datasetId : String = ""

  override def startRun(): Unit =
  {
    this.init()
    var deleteRequest : DeleteDatasetFromIdRequest = new DeleteDatasetFromIdRequest().sdkRequestConfig(this.getCustomSdkRequestConfig())
    deleteRequest.setBotId(botId)
    deleteRequest.setProjectId(projectId)
    deleteRequest.setDatasetId(datasetId)

    sdkClient.deletedatasetfromid(deleteRequest)
  }
}

@Command(name = "list", sortOptions = false,
  description = Array(
    "list datasets"),
  footer = Array()
)
class RunListDatasetCommand extends BaseSubCommand {

  @Option(names = Array("-bId", "--botId"), description = Array("set the botId"),required = true) private var botId : String = ""
  @Option(names = Array("-pId", "--projectId"), description = Array("set the projectId"),required = true) private var projectId : String = ""
  @Option(names = Array("-v", "--version"), description = Array("set the version"),required = true) private var version : String = ""
  @Option(names = Array("-st", "--startTimePeriod"), description = Array("set the startTimePeriood"),required = false) private var startTimePeriod : String = "01_01_1900_12_00_00"
  @Option(names = Array("-et", "--endTimePeriod"), description = Array("set the endTimePeriod"),required = false) private var endTimePeriod : String = "01_01_1900_12_00_00"

  override def startRun(): Unit =
  {
    this.init()
    var datasetRequest : Get_all_datasetsRequest = new Get_all_datasetsRequest().sdkRequestConfig(this.getCustomSdkRequestConfig())
    datasetRequest.setProjectId(projectId)
    datasetRequest.setBotId(botId)
    var datasets  = sdkClient.get_all_datasets(datasetRequest).getListDatasets.getDatasets.asScala
    this.dataGrid = new DataGrid("Id","ProjectId","BotId","Name","Comments","Version","InputDatabaseName","OutputDatabaseName","InputTableName","OutputTableName","Status","TargetPathInput","TargetPathOutput")
    datasets.foreach(item => {
      var request = new Get_dataset_versionRequest().sdkRequestConfig(this.getCustomSdkRequestConfig())
      request.setProjectId(projectId)
      request.setBotId(botId)
      request.setDatasetId(item.getId)
      request.setVersion(item.getVersion)
      var timePeriod = new TimePeriod();
      timePeriod.setStartTimePeriod(startTimePeriod)
      timePeriod.setEndTimePeriod(endTimePeriod)
      request.setTimePeriod(timePeriod)
     var dataset_version_result = sdkClient.get_dataset_version(request)
      this.dataGrid.add(item.getId,item.getProjectId,item.getBotId,item.getName,item.getComments,item.getVersion,dataset_version_result.getDatasetVersion.getInputdatabaseName,dataset_version_result.getDatasetVersion.getOutputdatabaseName, dataset_version_result.getDatasetVersion.getInputtableName,dataset_version_result.getDatasetVersion.getOutputtableName,dataset_version_result.getDatasetVersion.getStatus,dataset_version_result.getDatasetVersion.getTargetPath,dataset_version_result.getDatasetVersion.getTargetPathOutput)
    }
    )
    if (this.dataGrid.size > 0) {
      this.dataGrid.render
      System.out.println(this.dataGrid.size + " rows in set\n")
    }
    else System.out.println("Empty set\n")
  }
}


@Command(name = "add", sortOptions = false,
  description = Array(
    "Add Dataset"),
  footer = Array(),
  subcommands = Array()
)
class RunAddDatasetCommand extends BaseSubCommand {

  @Option(names = Array("-n", "--name"), description = Array("set the dataset name"),required = true) private var name : String = ""
  @Option(names = Array("-p", "--projectId"), description = Array("set the project id"),required = true) private var projectId : String = ""
  @Option(names = Array("-b", "--botId"), description = Array("set the bot id"),required = true) private var botId : String = ""
  @Option(names = Array("-c", "--comments"), description = Array("set the comments"),required = false) private var comments : String = ""
  @Option(names = Array("-a", "--attachmentName"), description = Array("set the attachment name"),required = true) private var attachmentName : String = ""
  @Option(names = Array("-f", "--filePath"), description = Array("set the file path of the dataset to upload"),required = true) private var filePath : String = ""
  @Option(names = Array("-v", "--version"), description = Array("set the version of the dataset"),required = true) private var version : String = ""
  @Option(names = Array("-h", "--headerLine"), description = Array("set the headerLine"),required = true) private var headerLine : String = ""

  override def startRun(): Unit =
  {
    this.init()
    var datasetRequest : Add_datasetRequest = new Add_datasetRequest().sdkRequestConfig(this.getCustomSdkRequestConfig())
    var dataset = new Dataset()
    dataset.setApiKey(RunWebRobotCli.config.getString("apikey"))

    dataset.setBotId(botId)
    dataset.setProjectId(projectId)
    dataset.setComments(comments)
    dataset.setName(name)
    var uploadFileRequest = new Upload_fileRequest().sdkRequestConfig(this.getCustomSdkRequestConfig())
    uploadFileRequest.setAttachmentName(attachmentName)
    uploadFileRequest.setBotId(botId)
    uploadFileRequest.setProjectId(projectId)
    var uploadFileResult = sdkClient.upload_file(uploadFileRequest)
    var url = uploadFileResult.getStringResult().getResult();
    var inputStream = new FileInputStream(filePath);
    uploadFile(new URL(url),inputStream);

    dataset.setVersion(version)
    dataset.setHeaderline(headerLine)
    dataset.setAttachmentName(attachmentName)
    var addDatasetRequest = new Add_datasetRequest().sdkRequestConfig(this.getCustomSdkRequestConfig())
    addDatasetRequest.setBotId(botId)
    addDatasetRequest.setProjectId(projectId)
    addDatasetRequest.setDataset(dataset)
    var datasetResult = sdkClient.add_dataset(addDatasetRequest)

  }
}


@Command(name = "dataset", sortOptions = false,
  description = Array(
    "Manage WebRobot Datasets (add,update,delte,List)"),
  footer = Array(),
  subcommands = Array(classOf[RunGetInputPaginationDatasetCommand],classOf[RunGetInputDatasetCommand],classOf[RunAddDatasetCommand],classOf[RunListDatasetCommand],classOf[RunDeleteDatasetCommand])
)
class RunDatasetCommand   extends Runnable  {

  def run(): Unit = {

    println("Run Dataset Command: add|list|delete|get")
  }
}