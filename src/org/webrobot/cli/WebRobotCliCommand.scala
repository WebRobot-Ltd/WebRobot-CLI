package org.webrobot.cli
import com.typesafe.config.Config
import picocli.CommandLine.{Command, Option}
import org.webrobot.cli.commands.{RunBotCommand, RunConceptCommand, RunDatasetCommand, RunImportExportCommand, RunJobCommand, RunProjectCommand, RunScriptCommand}
@Command(name = "main", sortOptions = false,
  description = Array(
    "Run WebRobot Cli command with support with relative sub commands (project|bot|dataset|script|job|concept,package)"),
  footer = Array(),
  subcommands = Array(classOf[RunProjectCommand],classOf[RunBotCommand],classOf[RunJobCommand],classOf[RunScriptCommand],classOf[RunDatasetCommand],classOf[RunConceptCommand],classOf[RunImportExportCommand])

)
class WebRobotCliCommand   extends Runnable  {

  def run(): Unit = {


  }
}