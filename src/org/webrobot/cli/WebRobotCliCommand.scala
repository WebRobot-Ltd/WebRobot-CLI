package org.webrobot.cli

import picocli.CommandLine.Command
import org.webrobot.cli.commands.{
  AgenticCommand,
  RunBotCommand,
  RunConceptCommand,
  RunDatasetCommand,
  RunImportExportCommand,
  RunJobCommand,
  RunProjectCommand,
  RunScriptCommand
}

@Command(
  name = "webrobot",
  mixinStandardHelpOptions = true,
  version = Array("webrobot-cli 0.3"),
  sortOptions = false,
  description = Array(
    "CLI WebRobot: comandi su progetto, bot, dataset, job, script, concept, import/export.",
    "Usa il client legacy AWS API Gateway (webrobot.eu:org.webrobot.sdk) verso l'API Jersey.",
    "Endpoint API: chiave `api_endpoint` in config (default https://api.webrobot.eu).",
    "SDK Maven: GitHub Packages WebRobot-Ltd/webrobot-sdk (stesso groupId/artifactId/versione del pom)."
  ),
  footer = Array(
    "",
    "Esempi:",
    "  webrobot project list",
    "  webrobot project get -n <projectId>",
    "  webrobot project schedule-get -i <projectId>",
    "",
    "Opzioni globali: -h / --help, -V / --version"
  ),
  subcommands = Array(
    classOf[RunProjectCommand],
    classOf[RunBotCommand],
    classOf[RunJobCommand],
    classOf[RunScriptCommand],
    classOf[RunDatasetCommand],
    classOf[RunConceptCommand],
    classOf[RunImportExportCommand],
    classOf[AgenticCommand]
  )
)
class WebRobotCliCommand extends Runnable {

  override def run(): Unit = ()
}
