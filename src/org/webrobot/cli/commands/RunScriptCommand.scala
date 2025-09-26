package org.webrobot.cli.commands
import picocli.CommandLine.{Command, Option}
import java.time.DateTimeException

import picocli.CommandLine.{Command, Option}
import WebRobot.Cli.Sdk.model.{Add_scriptRequest, Bot, BotScript, Create_botRequest, Delete_botRequest, Delete_projectRequest, Delete_scriptRequest, Get_all_botsRequest, Get_all_botsResult, Get_all_projectsRequest, Get_all_projectsResult, Get_scriptsRequest, Get_scriptsResult, ListBots, Project, Update_botRequest, Update_projectRequest, Update_scriptRequest}
import com.amazonaws.opensdk.config.{ConnectionConfiguration, TimeoutConfiguration}
import org.webrobot.cli.utils.DataGrid

import scala.collection.JavaConverters._
import scala.io.Source

@Command(name = "delete", sortOptions = false,
  description = Array(
    "delete script"),
  footer = Array()
)
class RunDeleteScriptCommand extends BaseSubCommand {

  @Option(names = Array("-i", "--id"), description = Array("set the script id"),required = true) private var scriptId : String = ""
  @Option(names = Array("-p", "--projectId"), description = Array("set the projectId"),required = true) private var projectId : String = ""
  @Option(names = Array("-b", "--botId"), description = Array("set the botId"),required = true) private var botId : String = ""

  override def startRun(): Unit =
  {
    this.init();
    var scriptRequest : Delete_scriptRequest = new Delete_scriptRequest().sdkRequestConfig(this.getCustomSdkRequestConfig())
    scriptRequest.setBotId(botId)
    scriptRequest.setProjectId(projectId)
    scriptRequest.setScriptId(scriptId)
    sdkClient.delete_script(scriptRequest)
  }
}
@Command(name = "list", sortOptions = false,
  description = Array(
    "list scripts"),
  footer = Array()
)
class RunListScriptCommand extends BaseSubCommand {

  @Option(names = Array("-i", "--id"), description = Array("set the bot id"),required = true) private var botId : String = ""
  @Option(names = Array("-pId", "--projectId"), description = Array("set the project id"),required = true) private var projectId : String = ""

  override def startRun(): Unit =
  {
    this.init()
    var scriptListRequest : Get_scriptsRequest = new Get_scriptsRequest().sdkRequestConfig(this.getCustomSdkRequestConfig())
    scriptListRequest.setBotId(botId)
    scriptListRequest.setProjectId(projectId)
    var scriptListResult : Get_scriptsResult = sdkClient.get_scripts(scriptListRequest)
    var scripts  = scriptListResult.getListScripts().getScripts.asScala
    this.dataGrid = new DataGrid("Id","ProjectId","BotId","Code","Created")
    scripts.foreach(item => {
      this.dataGrid.add(item.getId,item.getProjectId,item.getBotId,item.getCode, item.getCreatedTime)
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
    "update script"),
  footer = Array()
)
class RunUpdateScriptCommand extends BaseSubCommand {

  @Option(names = Array("-i", "--id"), description = Array("set the script"),required = true) private var scriptId : String = ""
  @Option(names = Array("-bId", "--botId"), description = Array("set the script"),required = true) private var botId : String = ""
  @Option(names = Array("-pId", "--projectId"), description = Array("set the projectId"),required = true) private var projectId : String = ""
  @Option(names = Array("-c", "--codeFile"), description = Array("set the bot code"),required = false) private var scriptCodeFile : String = ""
  override def startRun(): Unit =
  {
    this.init()
    var scriptRequest : Update_scriptRequest = new Update_scriptRequest().sdkRequestConfig(this.getCustomSdkRequestConfig())
    var script : BotScript = new BotScript()
    script.setId(scriptId)
    script.setBotId(botId)
    var scriptCode = ""
    for(line <-  Source.fromFile(scriptCodeFile).getLines())
    {
      scriptCode = scriptCode + "\r\n" + line;
    }
    script.setCode(scriptCode)
    script.setProjectId(projectId)
    script.setType("Client")
    scriptRequest.setBotScript(script)
    scriptRequest.setProjectId(projectId)
    scriptRequest.setBotId(botId)
    scriptRequest.setScriptId(scriptId)
    scriptRequest.setBotScript(script)
    sdkClient.update_script(scriptRequest)
  }
}

@Command(name = "add", sortOptions = false,
  description = Array(
    "Add new script"),
  footer = Array(),
  subcommands = Array()
)
class RunAddScriptCommand extends BaseSubCommand {


  @Option(names = Array("-pId", "--projectId"), description = Array("set the projectId"),required = true) private var projectId : String = ""
  @Option(names = Array("-bId", "--botId"), description = Array("set the botId"),required = true) private var botId : String = ""
  @Option(names = Array("-c", "--codeFile"), description = Array("set the bot code"),required = false) private var scriptCodeFile : String = ""
  override def startRun(): Unit =
  {
    this.init()
    var botScript : BotScript = new BotScript()
    botScript.setCreatedTime(java.time.LocalDateTime.now().toString)
    var scriptCode = ""
    for(line <-  Source.fromFile(scriptCodeFile).getLines())
    {
      scriptCode = scriptCode + "\r\n" + line;
    }
    botScript.setCode(scriptCode)
    botScript.setProjectId(projectId)
    botScript.setBotId(botId)
    var scriptRequest : Add_scriptRequest = new Add_scriptRequest().sdkRequestConfig(this.getCustomSdkRequestConfig())
    scriptRequest.setProjectId(projectId)
    scriptRequest.setBotId(botId)
    scriptRequest.setBotScript(botScript)
    sdkClient.add_script(scriptRequest)
  }
}

@Command(name = "script", sortOptions = false,
  description = Array(
    "Manage WebRobot scripts (Insert,Update,Remove,List)"),
  footer = Array(),
  subcommands = Array(classOf[RunAddScriptCommand],classOf[RunUpdateScriptCommand],classOf[RunListScriptCommand],classOf[RunDeleteScriptCommand])
)
class RunScriptCommand   extends Runnable {

  def run(): Unit = {

    println("Run Script Command: add|update|list|delete")
  }
}