package org.webrobot.cli.commands
import java.io.InputStreamReader
import java.time.DateTimeException

import picocli.CommandLine.{Command, Option}
import WebRobot.Cli.Sdk.model.{Bot, Create_botRequest, Delete_botRequest, Delete_projectRequest, GetBotFromIdRequest, GetBotFromIdResult, GetBotFromNameRequest, GetBotFromNameResult, Get_all_botsRequest, Get_all_botsResult, Get_all_projectsRequest, Get_all_projectsResult, ListBots, Project, Update_botRequest, Update_projectRequest}
import com.amazonaws.opensdk.config.{ConnectionConfiguration, TimeoutConfiguration}
import org.webrobot.cli.utils.DataGrid

import scala.collection.JavaConverters._
import scala.io.Source

@Command(name = "delete", sortOptions = false,
  description = Array(
    "delete bot"),
  footer = Array()
)
class RunDeleteBotCommand extends BaseSubCommand {

  @Option(names = Array("-i", "--id"), description = Array("set the project id"),required = true) private var botId : String = ""
  @Option(names = Array("-p", "--projectId"), description = Array("set the projectId"),required = true) private var projectId : String = ""

  override def startRun(): Unit =
  {
    this.init()
    var botRequest : Delete_botRequest = new Delete_botRequest().sdkRequestConfig(this.getCustomSdkRequestConfig())
    botRequest.setBotId(botId)
    botRequest.setProjectId(projectId)
    sdkClient.delete_bot(botRequest)
  }
}


@Command(name = "getfromname", sortOptions = false,
  description = Array(
    "get bot"),
  footer = Array()
)
class GetBotFromNameCommand extends BaseSubCommand {
  @Option(names = Array("-p", "--projectId"), description = Array("set the projectId"),required = true) private var projectId : String = ""
  @Option(names = Array("-n", "--name"), description = Array("set the bot name"),required = true) private var botName : String = ""
  override def startRun(): Unit =
  {
    this.init()
    var getBotNameRequest: GetBotFromNameRequest = new GetBotFromNameRequest().sdkRequestConfig(this.getCustomSdkRequestConfig())
    getBotNameRequest.setProjectId(projectId)
    getBotNameRequest.setBotName(botName)
    var getBotResult : GetBotFromNameResult = sdkClient.getbotfromname(getBotNameRequest)

    this.dataGrid = new DataGrid("Id","Name","Description","Code","Created")
    this.dataGrid.add(getBotResult.getBot.getId,getBotResult.getBot.getName,getBotResult.getBot.getDescription,getBotResult.getBot.getCode,getBotResult.getBot.getCreatedTime)

    if (this.dataGrid.size > 0) {
      this.dataGrid.render
      System.out.println(this.dataGrid.size + " rows in set\n")
    }
    else System.out.println("Empty set\n")
  }
}

@Command(name = "get", sortOptions = false,
  description = Array(
    "get bot"),
  footer = Array()
)
class GetBotFromIdCommand extends BaseSubCommand {
  @Option(names = Array("-p", "--projectId"), description = Array("set the projectId"),required = true) private var projectId : String = ""
  @Option(names = Array("-i", "--id"), description = Array("set the bot id"),required = true) private var botId : String = ""
  override def startRun(): Unit =
  {
    this.init()
    var getBotRequest : GetBotFromIdRequest = new GetBotFromIdRequest().sdkRequestConfig(this.getCustomSdkRequestConfig())
    getBotRequest.setProjectId(projectId)
    getBotRequest.setBotId(botId)
    var getBotResult : GetBotFromIdResult = sdkClient.getbotfromid(getBotRequest)

    this.dataGrid = new DataGrid("Id","Name","Description","Code","Created")
    this.dataGrid.add(getBotResult.getBot.getId,getBotResult.getBot.getName,getBotResult.getBot.getDescription,getBotResult.getBot.getCode,getBotResult.getBot.getCreatedTime)

    if (this.dataGrid.size > 0) {
      this.dataGrid.render
      System.out.println(this.dataGrid.size + " rows in set\n")
    }
    else System.out.println("Empty set\n")
  }
}

@Command(name = "list", sortOptions = false,
  description = Array(
    "list bots"),
  footer = Array()
)
class RunListBotCommand extends BaseSubCommand {
  @Option(names = Array("-p", "--projectId"), description = Array("set the projectId"),required = true) private var projectId : String = ""
  override def startRun(): Unit =
  {
    this.init()
    var botListRequest : Get_all_botsRequest = new Get_all_botsRequest().sdkRequestConfig(this.getCustomSdkRequestConfig())
    botListRequest.setProjectId(projectId)
    var botListResult : Get_all_botsResult = sdkClient.get_all_bots(botListRequest)
    var bots  = botListResult.getListBots().getBots.asScala
    this.dataGrid = new DataGrid("Id","Name","Description","Code","Created")
    bots.foreach(item => {
      this.dataGrid.add(item.getId,item.getName,item.getDescription,item.getCode,item.getCreatedTime)
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
    "update bot"),
  footer = Array()
)
class RunUpdateBotCommand extends BaseSubCommand {

  @Option(names = Array("-p", "--projectId"), description = Array("set the projectId"),required = true) private var projectId : String = ""
  @Option(names = Array("-i", "--id"), description = Array("set the bot id"),required = true) private var botId : String = ""
  @Option(names = Array("-n", "--name"), description = Array("set the bot name"),required = true) private var botName : String = ""
  @Option(names = Array("-d", "--description"), description = Array("set the bot description"),required = true) private var botDescription : String = ""
  @Option(names = Array("-c", "--codeFile"), description = Array("set the bot code"),required = false) private var botCodeFile : String = ""
  override def startRun(): Unit =
  {
    this.init()
    var botRequest : Update_botRequest = new Update_botRequest().sdkRequestConfig(this.getCustomSdkRequestConfig())

    var bot : Bot = new Bot()
    bot.setName(botName)
    bot.setDescription(botDescription)
    var botCode = ""
    for(line <-  Source.fromFile(botCodeFile).getLines())
    {
      botCode = botCode + "\r\n" + line;
    }
    bot.setCode(botCode)
    bot.setId(botId)
    bot.setProjectId(projectId)
    botRequest.setBot(bot)
    botRequest.setBotId(botId)
    botRequest.setProjectId(projectId)
    sdkClient.update_bot(botRequest)
  }
}

@Command(name = "add", sortOptions = false,
  description = Array(
    "Add new bot"),
  footer = Array(),
  subcommands = Array()
)
class RunAddBotCommand extends BaseSubCommand {

  @Option(names = Array("-n", "--name"), description = Array("set the bot name"),required = true) private var botName : String = ""
  @Option(names = Array("-d", "--description"), description = Array("set the bot description"),required = true) private var botDescription : String = ""
  @Option(names = Array("-c", "--codeFile"), description = Array("set the bot code"),required = false) private var botCodeFile : String = ""
  @Option(names = Array("-p", "--projectId"), description = Array("set the projectId"),required = true) private var projectId : String = ""

  override def startRun(): Unit =
  {
    this.init()
    var bot : Bot = new Bot()
    bot.setName(botName)
    bot.setDescription(botDescription)
    bot.setCreatedTime(java.time.LocalDateTime.now().toString)
    var botCode = ""
    for(line <-  Source.fromFile(botCodeFile).getLines())
    {
      botCode = botCode + "\r\n" + line;
    }
    bot.setCode(botCode)
    bot.setProjectId(projectId)
    var botRequest : Create_botRequest = new Create_botRequest().sdkRequestConfig(this.getCustomSdkRequestConfig())
    botRequest.setProjectId(projectId)
    botRequest.setBot(bot)
    sdkClient.create_bot(botRequest)
  }
}

@Command(name = "bot", sortOptions = false,
  description = Array(
    "Manage WebRobot bots (Insert|Update|Remove|List|get|getfromname)"),
  footer = Array(),
  subcommands = Array(classOf[GetBotFromNameCommand],classOf[GetBotFromIdCommand],classOf[RunAddBotCommand],classOf[RunUpdateBotCommand],classOf[RunListBotCommand],classOf[RunDeleteBotCommand])
)
class RunBotCommand   extends Runnable  {

  def run(): Unit = {

    println("Run Bot Command: add|update|list|delete")
  }
}