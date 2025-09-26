package org.webrobot.cli.commands
import java.time.DateTimeException

import WebRobot.Cli.Sdk.model._
import picocli.CommandLine.{Command, Option}
import com.amazonaws.opensdk.config.{ConnectionConfiguration, TimeoutConfiguration}
import org.webrobot.cli.utils.DataGrid

import scala.collection.JavaConverters._
import java.io.{File, FileInputStream, FileOutputStream, IOException, InputStream, OutputStream}

import WebRobot.Cli.Sdk.Utils.Utils

import scala.collection.mutable.ArrayBuffer

@Command(name = "getoutputpagination", sortOptions = false,
description = Array(
"Get output of dataset with pagination"),
footer = Array()
)
class RunGetOutputJobPaginationCommand extends BaseSubCommand {
  @Option(names = Array("-i", "--id"), description = Array("set the project id"),required = true) private var botId : String = ""
  @Option(names = Array("-p", "--projectId"), description = Array("set the projectId"),required = true) private var projectId : String = ""
  @Option(names = Array("-d", "--datasetId"), description = Array("set the datasetId"),required = true) private var datasetId : String = ""
  @Option(names = Array("-j", "--jobId"), description = Array("set the jobId"),required = true) private var jobId : String = ""
  @Option(names = Array("-f", "--fileName"), description = Array("file name"),required = true) private var fileName : String = ""
  @Option(names = Array("-v", "--version"), description = Array("version"),required = true) private var version : String = ""
  @Option(names = Array("-st", "--startTimePeriod"), description = Array("startTimePeriod"),required = true) private var startTimePeriod : String = ""
  @Option(names = Array("-et", "--endTimePeriod"), description = Array("endTimePeriod"),required = true) private var endTimePeriod : String = ""
  @Option(names = Array("-l", "--limit"), description = Array("limit"),required = true) private var limit : String = ""
  @Option(names = Array("-o", "--offset"), description = Array("offset"),required = true) private var offset : String = ""
  override def startRun(): Unit =
  {
    try {
      this.init()
      var datasetRequest: Get_output_file_paginationRequest = new Get_output_file_paginationRequest().sdkRequestConfig(this.getCustomSdkRequestConfig())
      datasetRequest.setBotId(botId)
      datasetRequest.setProjectId(projectId)
      datasetRequest.setDatasetId(datasetId)
      datasetRequest.setJobId(jobId)
      var timePeriod = new TimePeriod();
      timePeriod.setStartTimePeriod(startTimePeriod)
      timePeriod.setEndTimePeriod(endTimePeriod)
      datasetRequest.setTimePeriod(timePeriod)

      datasetRequest.setLimit(limit)
      datasetRequest.setOffset(offset)
      var result = sdkClient.get_output_file_pagination(datasetRequest);

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


@Command(name = "getlistdatasetversionsjob", sortOptions = false,
  description = Array(
    "List of all dataset of tasks of jobs"),
  footer = Array()
)
class RunGetListDatasetVersionsJobCommand extends BaseSubCommand {

  @Option(names = Array("-i", "--id"), description = Array("set the project id"),required = true) private var botId : String = ""
  @Option(names = Array("-p", "--projectId"), description = Array("set the projectId"),required = true) private var projectId : String = ""
  @Option(names = Array("-d", "--datasetId"), description = Array("set the datasetId"),required = true) private var datasetId : String = ""
  @Option(names = Array("-j", "--jobId"), description = Array("set the jobId"),required = true) private var jobId : String = ""
  @Option(names = Array("-f", "--fileName"), description = Array("file name"),required = true) private var fileName : String = ""
  @Option(names = Array("-v", "--version"), description = Array("version"),required = true) private var version : String = ""

  override def startRun(): Unit =
  {
    try {
      this.init()
      var datasetRequest: Get_dataset_version_of_tasksRequest = new Get_dataset_version_of_tasksRequest().sdkRequestConfig(this.getCustomSdkRequestConfig())
      datasetRequest.setBotId(botId)
      datasetRequest.setProjectId(projectId)
      datasetRequest.setDatasetId(datasetId)
      datasetRequest.setJobId(jobId)
      datasetRequest.setVersion(version)
      var result = sdkClient.get_dataset_version_of_tasks(datasetRequest);
      var versions = result.getListDatasetVersions.getVersions.asScala
      this.dataGrid = new DataGrid("Id","ApiKey","ProjectId","BotId","DatasetId","JobId","Version","TimePeriod","InputDatabaseName","InputTableName","OutputDatabaseName","TargetPathInput","TargetPathOutput")
      versions.foreach(item => {

        this.dataGrid.add(item.getId,item.getApikey,item.getProjectId,item.getBotId,item.getDatasetId,item.getJobId,item.getVersion,item.getTimePeriod,item.getInputdatabaseName,item.getInputtableName,item.getTargetPath,item.getTargetPathOutput)
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
        case e: IOException => println("An error occurred while downloading the file")
      }
  }

}


@Command(name = "getoutput", sortOptions = false,
  description = Array(
    "output of the job"),
  footer = Array()
)
class RunGetOutputJobCommand extends BaseSubCommand {

  @Option(names = Array("-i", "--id"), description = Array("set the project id"),required = true) private var botId : String = ""
  @Option(names = Array("-p", "--projectId"), description = Array("set the projectId"),required = true) private var projectId : String = ""
  @Option(names = Array("-d", "--datasetId"), description = Array("set the datasetId"),required = true) private var datasetId : String = ""
  @Option(names = Array("-j", "--jobId"), description = Array("set the jobId"),required = true) private var jobId : String = ""
  @Option(names = Array("-f", "--fileName"), description = Array("file name"),required = true) private var fileName : String = ""
  @Option(names = Array("-st", "--startTimePeriod"), description = Array("start time period"),required = true) private var startTimePeriod : String = ""
  @Option(names = Array("-et", "--endTimePeriod"), description = Array("end time period"),required = true) private var endTimePeriod : String = ""
  override def startRun(): Unit =
  {
    try {
      this.init()
      var jobRequest: Get_output_fileRequest = new Get_output_fileRequest().sdkRequestConfig(this.getCustomSdkRequestConfig())
      jobRequest.setBotId(botId)
      jobRequest.setProjectId(projectId)
      jobRequest.setDatasetId(datasetId)
      jobRequest.setJobId(jobId)
      var timePeriod = new TimePeriod();
      timePeriod.setStartTimePeriod(startTimePeriod)
      timePeriod.setEndTimePeriod(endTimePeriod)
      var result = sdkClient.get_output_file(jobRequest);
      var s3Url = result.getStringResult.getResult
      Utils.downloadFile(s3Url,this.fileName)
    }
    catch
    {
      case e: IOException => println("An error occurred while downloading the file")
    }
  }

}
@Command(name = "delete", sortOptions = false,
  description = Array(
    "delete job"),
  footer = Array()
)
class RunDeleteJobCommand extends BaseSubCommand {

  @Option(names = Array("-b", "--botId"), description = Array("set the bot id"),required = true) private var botId : String = ""
  @Option(names = Array("-p", "--projectId"), description = Array("set the projectId"),required = true) private var projectId : String = ""
  @Option(names = Array("-d", "--datasetId"), description = Array("set the datasetId"),required = true) private var datasetId : String = ""
  @Option(names = Array("-j", "--jobId"), description = Array("set the jobId"),required = true) private var jobId : String = ""

  override def startRun(): Unit =
  {
    this.init()
    var jobRequest : Delete_jobRequest = new Delete_jobRequest().sdkRequestConfig(this.getCustomSdkRequestConfig())
    jobRequest.setBotId(botId)
    jobRequest.setProjectId(projectId)
    jobRequest.setDatasetId(datasetId)
    jobRequest.setJobId(jobId)
    sdkClient.delete_job(jobRequest)
  }
}
@Command(name = "list", sortOptions = false,
  description = Array(
    "list jobs"),
  footer = Array()
)
class RunListJobCommand extends BaseSubCommand {
  @Option(names = Array("-b", "--botId"), description = Array("set the bot id"),required = true) private var botId : String = ""
  @Option(names = Array("-p", "--projectId"), description = Array("set the projectId"),required = true) private var projectId : String = ""
  @Option(names = Array("-d", "--datasetId"), description = Array("set the datasetId"),required = true) private var datasetId : String = ""
  override def startRun(): Unit =
  {
    this.init()
    var jobListRequest : Get_all_jobsRequest  = new Get_all_jobsRequest().sdkRequestConfig(this.getCustomSdkRequestConfig())
    jobListRequest.setProjectId(projectId)
    jobListRequest.setBotId(botId)
    jobListRequest.setDatasetId(datasetId)
    var jobListResult : Get_all_jobsResult = sdkClient.get_all_jobs(jobListRequest)

    var jobs  = jobListResult.getListJobs().getJobs.asScala
    this.dataGrid = new DataGrid("Id","BotId","DatasetId","IsImmediate","ScheduleInfo","Status")
    jobs.foreach(item => {
      this.dataGrid.add(item.getId,item.getBotId,item.getDatasetId, item.getIsImmediate,item.getScheduleInfo, item.getStatus)
    }
    )
    if (this.dataGrid.size > 0) {
      this.dataGrid.render
      System.out.println(this.dataGrid.size + " rows in set\n")
    }
    else System.out.println("Empty set\n")
  }
}
@Command(name = "update", sortOptions = false,
  description = Array(
    "update job"),
  footer = Array()
)
class RunUpdateJobCommand extends BaseSubCommand {

  @Option(names = Array("-i", "--id"), description = Array("set the bot id"),required = true) private var jobId : String = ""
  @Option(names = Array("-dId", "--datasetId"), description = Array("set the dataset id"),required = true) private var datasetId : String = ""
  @Option(names = Array("-pId", "--projectId"), description = Array("set the project id"),required = true) private var projectId : String = ""
  @Option(names = Array("-bId", "--botId"), description = Array("set the bot id"),required = true) private var botId : String = ""
  @Option(names = Array("-isI", "--isImmediate"), description = Array("is immediate"),required = true) private var isImmediate : String = ""
  @Option(names = Array("-m", "--minutes"), description = Array("set the minutes of schedule job"),required = true) private var minutes : String = ""
  @Option(names = Array("-o", "--ours"), description = Array("set the ours of schedule job"),required = true) private var ours : String = ""
  @Option(names = Array("-d", "--dayOfMonth"), description = Array("set the day of month of schedule job"),required = true) private var dayOfMonth : String = ""
  @Option(names = Array("-mo", "--month"), description = Array("set the month of schedule job"),required = true) private var month : String = ""
  @Option(names = Array("-dw", "--dayOfweek"), description = Array("set the day of week of schedule job"),required = true) private var dayOfweek : String = ""
  @Option(names = Array("-y", "--year"), description = Array("set the year of schedule job"),required = true) private var year : String = ""

  override def startRun(): Unit =
  {
    this.init()
    var jobRequest : Update_jobRequest = new Update_jobRequest().sdkRequestConfig(this.getCustomSdkRequestConfig())
    var job : Job = new Job()
    job.setId(jobId)
    var scheduleInfo = "cron("  + minutes + " " + ours + " " + dayOfMonth + " " + month+ " "  +  dayOfweek + " " + year + ")"
    job.setScheduleInfo(scheduleInfo)
    job.setIsImmediate(  isImmediate.toBoolean)
    jobRequest.setProjectId(projectId)
    jobRequest.setBotId(botId)
    jobRequest.setJob(job)
    sdkClient.update_job(jobRequest)
  }
}

@Command(name = "add", sortOptions = false,
  description = Array(
    "Add new job"),
  footer = Array(),
  subcommands = Array()
)
class RunAddJobCommand extends BaseSubCommand {


  @Option(names = Array("-dId", "--datasetId"), description = Array("set the dataset id"),required = true) private var datasetId : String = ""
  @Option(names = Array("-pId", "--projectId"), description = Array("set the project id"),required = true) private var projectId : String = ""
  @Option(names = Array("-bId", "--botId"), description = Array("set the bot id"),required = true) private var botId : String = ""
  //@Option(names = Array("-s", "--scheduleInfo"), description = Array("set the scheduleInfo of the job"),required = false) private var scheduleInfo : String = ""
  @Option(names = Array("-isI", "--isImmediate"), description = Array("is immediate"),required = false) private var isImmediate : String = "False"
  @Option(names = Array("-m", "--minutes"), description = Array("set the minutes of schedule job"),required = true) private var minutes : String = ""
  @Option(names = Array("-o", "--ours"), description = Array("set the ours of schedule job"),required = true) private var ours : String = ""
  @Option(names = Array("-d", "--dayOfMonth"), description = Array("set the day of month of schedule job"),required = true) private var dayOfMonth : String = ""
  @Option(names = Array("-mo", "--month"), description = Array("set the month of schedule job"),required = true) private var month : String = ""
  @Option(names = Array("-dw", "--dayOfweek"), description = Array("set the day of week of schedule job"),required = true) private var dayOfweek : String = ""
  @Option(names = Array("-y", "--year"), description = Array("set the year of schedule job"),required = true) private var year : String = ""
  override def startRun(): Unit =
  {
    this.init()
    var jobRequest : Create_jobRequest = new Create_jobRequest().sdkRequestConfig(this.getCustomSdkRequestConfig())
    var job : Job = new Job()
    var scheduleInfo = "cron("  + minutes + " " + ours + " " + dayOfMonth + " " + month+ " "  +  dayOfweek + " " + year + ")"
    //scheduleInfo = "cron(26 16 * * ? *)"
    job.setIsImmediate(  isImmediate.toBoolean)
    job.setScheduleInfo(scheduleInfo)
    jobRequest.setProjectId(projectId)
    jobRequest.setBotId(botId)
    jobRequest.setDatasetId(datasetId)
    jobRequest.setJob(job)
    sdkClient.create_job(jobRequest)


  }
}

@Command(name = "job", sortOptions = false,
  description = Array(
    "Manage WebRobot job (Insert,Update,Remove,List)"),
  footer = Array(),
  subcommands = Array(classOf[RunGetOutputJobCommand],classOf[RunGetListDatasetVersionsJobCommand],classOf[RunGetOutputJobPaginationCommand],classOf[RunAddJobCommand],classOf[RunUpdateJobCommand],classOf[RunListJobCommand],classOf[RunDeleteJobCommand])
)
class RunJobCommand   extends Runnable  {

  def run(): Unit = {

    println("Run Job Command:add|update|list|delete|getoutputpagination|getoutput|getlistdatasetversionsjob")
  }
}