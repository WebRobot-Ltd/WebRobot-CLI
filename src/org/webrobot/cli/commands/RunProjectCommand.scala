package org.webrobot.cli.commands
import java.time.DateTimeException

import picocli.CommandLine.{Command, Option}
import WebRobot.Cli.Sdk.model.{Create_projectRequest, Delete_projectRequest, Get_all_projectsRequest, Get_all_projectsResult, Get_projectRequest, Get_projectResult, Get_project_scheduleRequest, Get_project_scheduleResult, Project, ProjectSchedule, Set_project_scheduleRequest, Set_project_scheduleResult, Update_projectRequest}
import com.amazonaws.opensdk.SdkRequestConfig
import com.amazonaws.opensdk.config.{ConnectionConfiguration, TimeoutConfiguration}
import org.webrobot.cli.RunWebRobotCli
import org.webrobot.cli.utils.DataGrid

import scala.collection.JavaConverters._

@Command(name = "delete", sortOptions = false,
  description = Array(
    "delete project"),
  footer = Array()
)
class RunDeleteProjectCommand extends BaseSubCommand {

  @Option(names = Array("-i", "--projectId"), description = Array("set the project id"),required = true) private var projectId : String = ""

  override def startRun(): Unit =
  {
    this.init()
    var projectRequest : Delete_projectRequest = new Delete_projectRequest().sdkRequestConfig(this.getCustomSdkRequestConfig())
    projectRequest.setProjectId(projectId)

    sdkClient.delete_project(projectRequest)
  }
}

@Command(name = "list", sortOptions = false,
  description = Array(
    "list projects"),
  footer = Array()
)
class RunListProjectCommand extends BaseSubCommand {
  override def startRun(): Unit =
  {
    this.init()
    var projectListRequest : Get_all_projectsRequest = new Get_all_projectsRequest().sdkRequestConfig(this.getCustomSdkRequestConfig())
    var projectListResult : Get_all_projectsResult = sdkClient.get_all_projects(projectListRequest)
    if(projectListResult.getListProjects().getProjects() != null) {
      var projects: Seq[Project] = projectListResult.getListProjects().getProjects().asScala
      this.dataGrid = new DataGrid("Id", "Name", "Description", "Created")
      projects.foreach(item => {
        this.dataGrid.add(item.getId, item.getName, item.getDescription, item.getCreatedTime)
      }
      )
      if (this.dataGrid.size > 0) {
        this.dataGrid.render
        System.out.println(this.dataGrid.size + " rows in set\n")
      }
      else System.out.println("Empty set\n")
    }
    else System.out.println("Empty set\n")
  }
}
@Command(name = "update", sortOptions = false,
  description = Array(
    "update project"),
  footer = Array()
)
class RunUpdateProjectCommand extends BaseSubCommand {

  @Option(names = Array("-i", "--id"), description = Array("set the project id"),required = true) private var projectId : String = ""
  @Option(names = Array("-n", "--name"), description = Array("set the project name"),required = true) private var projectName : String = ""
  @Option(names = Array("-d", "--description"), description = Array("set the project description"),required = true) private var projectDescription : String = ""

  override def startRun(): Unit =
  {
    this.init()
    var projectRequest : Update_projectRequest = new Update_projectRequest().sdkRequestConfig(this.getCustomSdkRequestConfig())
    var project : Project = new Project()
    projectRequest.setProjectId(projectId)
    project.setName(projectName)
    project.setDescription(projectDescription)
    project.setId(projectId)
    projectRequest.setProject(project)
    sdkClient.update_project(projectRequest)
    System.out.println("projectID:" + projectId)
  }
}

@Command(name = "get", sortOptions = false,
description = Array(
"get project"),
footer = Array(),
subcommands = Array()
)
class RunGetProjectCommand extends BaseSubCommand {

  @Option(names = Array("-n", "--projectId"), description = Array("set the projectId"),required = true) private var projectId : String = ""
  override def startRun(): Unit =
  {
    this.init()
    var projectRequest : Get_projectRequest = new Get_projectRequest().sdkRequestConfig(this.getCustomSdkRequestConfig())
    projectRequest.setProjectId(projectId)
    var result =  sdkClient.get_project(projectRequest)
    if(result.getProject!= null) {
      this.dataGrid = new DataGrid("Id", "Name", "Description", "Created")
      this.dataGrid.add(result.getProject.getId, result.getProject.getName, result.getProject.getDescription, result.getProject.getCreatedTime)
      if (this.dataGrid.size > 0) {
        this.dataGrid.render
        System.out.println(this.dataGrid.size + " rows in set\n")
      }
      else System.out.println("Empty set\n")
    }
    else System.out.println("Empty set\n")
  }
}


@Command(name = "add", sortOptions = false,
  description = Array(
    "Add new project"),
  footer = Array(),
  subcommands = Array()
)
class RunAddProjectCommand extends BaseSubCommand {

  @Option(names = Array("-n", "--name"), description = Array("set the project name"),required = true) private var projectName : String = ""
  @Option(names = Array("-d", "--description"), description = Array("set the project description"),required = true) private var projectDescription : String = ""

  override def startRun(): Unit =
 {
   this.init()
   var projectRequest : Create_projectRequest = new Create_projectRequest().sdkRequestConfig(this.getCustomSdkRequestConfig())
   var project : Project = new Project()
   project.setName(projectName)
   project.setDescription(projectDescription)
   project.setCreatedTime(java.time.LocalDateTime.now().toString)
   projectRequest.setProject(project)
   sdkClient.create_project(projectRequest)
  }
}

@Command(name = "schedule-get", sortOptions = false,
  description = Array("get project ETL schedule (GET /projects/id/{id}/schedule)"),
  footer = Array()
)
class RunGetProjectScheduleCommand extends BaseSubCommand {

  @Option(names = Array("-i", "--projectId"), description = Array("project id"), required = true)
  private var projectId: String = ""

  override def startRun(): Unit = {
    this.init()
    val req = new Get_project_scheduleRequest().sdkRequestConfig(this.getCustomSdkRequestConfig())
    req.setProjectId(projectId)
    val result: Get_project_scheduleResult = sdkClient.get_project_schedule(req)
    val s = result.getSchedule
    if (s == null) {
      System.out.println("(no schedule payload)")
      return
    }
    System.out.println(
      s"""projectId=${scala.Option(s.getProjectId).getOrElse("")}
         |cronSchedule=${scala.Option(s.getCronSchedule).getOrElse("")}
         |enabled=${scala.Option(s.getEnabled).map(_.booleanValue).getOrElse(false)}
         |timezone=${scala.Option(s.getTimezone).getOrElse("")}
         |jobId=${scala.Option(s.getJobId).getOrElse("")}
         |cronJobName=${scala.Option(s.getCronJobName).getOrElse("")}
         |cronJobActive=${scala.Option(s.getCronJobActive).map(_.booleanValue).getOrElse(false)}
         |nextExecution=${scala.Option(s.getNextExecution).getOrElse("")}
         |message=${scala.Option(s.getMessage).getOrElse("")}
         |""".stripMargin)
  }
}

@Command(name = "schedule-set", sortOptions = false,
  description = Array("set project ETL schedule (PUT /projects/id/{id}/schedule); maps to Jersey ProjectScheduleRequest"),
  footer = Array()
)
class RunSetProjectScheduleCommand extends BaseSubCommand {

  @Option(names = Array("-i", "--projectId"), description = Array("project id"), required = true)
  private var projectId: String = ""

  @Option(names = Array("-j", "--jobId"), description = Array("job id (jobs.id) when schedule is enabled"))
  private var jobId: String = ""

  @Option(names = Array("-c", "--cron"), description = Array("cron expression (default 0 0 * * *)"))
  private var cron: String = "0 0 * * *"

  @Option(names = Array("-e", "--enabled"), description = Array("true|false (default true)"))
  private var enabledStr: String = "true"

  @Option(names = Array("-z", "--timezone"), description = Array("IANA or label (default UTC)"))
  private var timezone: String = "UTC"

  @Option(names = Array("--execution-json"), description = Array("optional JSON string for execute POST body"))
  private var executionJson: String = ""

  override def startRun(): Unit = {
    this.init()
    val enabled = java.lang.Boolean.parseBoolean(enabledStr)
    val sch = new ProjectSchedule()
    sch.setCronSchedule(cron)
    sch.setEnabled(java.lang.Boolean.valueOf(enabled))
    sch.setTimezone(timezone)
    if (jobId != null && jobId.nonEmpty) sch.setJobId(jobId)
    if (executionJson != null && executionJson.nonEmpty) sch.setExecutionRequestJson(executionJson)

    val req = new Set_project_scheduleRequest().sdkRequestConfig(this.getCustomSdkRequestConfig())
    req.setProjectId(projectId)
    req.setSchedule(sch)
    val result: Set_project_scheduleResult = sdkClient.set_project_schedule(req)
    val s = result.getSchedule
    if (s != null) {
      System.out.println(scala.Option(s.getMessage).getOrElse("OK"))
      if (s.getCronJobName != null) System.out.println("cronJobName=" + s.getCronJobName)
    } else System.out.println("OK")
  }
}

@Command(name = "project", sortOptions = false,
  description = Array(
    "Manage WebRobot Projects (Insert,Update,Remove,List)"),
  footer = Array(),

  subcommands = Array(classOf[RunAddProjectCommand], classOf[RunGetProjectCommand], classOf[RunUpdateProjectCommand], classOf[RunListProjectCommand], classOf[RunDeleteProjectCommand], classOf[RunGetProjectScheduleCommand], classOf[RunSetProjectScheduleCommand])
)
class RunProjectCommand   extends Runnable  {

  def run(): Unit = {
    System.err.println(
      "Uso: webrobot project <sottocomando>. Sottocomandi: add | get | update | list | delete | schedule-get | schedule-set"
    )
    System.err.println("Esempio: webrobot project list")
  }
}