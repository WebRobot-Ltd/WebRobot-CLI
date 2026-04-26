package org.webrobot.cli

import picocli.CommandLine.Command
import org.webrobot.cli.commands.{
  RunAgentCommand,
  RunCategoryCommand,
  RunDatasetCommand,
  RunImportExportCommand,
  RunJobCommand,
  RunProjectCommand
}

/** Radice Picocli: `project`, `category`, `agent`, `job`, `dataset`, `package`. */
@Command(
  name = "webrobot",
  mixinStandardHelpOptions = true,
  version = Array("webrobot-cli 0.3"),
  sortOptions = false,
  description = Array(
    "CLI WebRobot: comandi su progetto, category (categorie job), agent, dataset, job, package (import/export).",
    "Gruppo `agentic` (backend FastAPI separato) non incluso finché il flusso ETL non è completato.",
    "Gruppi dismessi (non esposti): page, concept, script (vecchia API Gateway / wrapper).",
    "Client API: JAR webrobot.eu:org.webrobot.sdk:0.3.10 da GitHub Packages (repository WebRobot-Ltd/webrobot-sdk).",
    "Endpoint API: chiave `api_endpoint` in config (default https://api.webrobot.eu).",
    "Percorso artifact sul registry: webrobot/eu/org.webrobot.sdk/0.3.10/org.webrobot.sdk-0.3.10.jar."
  ),
  footer = Array(
    "",
    "Gruppi: project | category | agent | job | dataset | package",
    "Esempi:",
    "  webrobot project list",
    "  webrobot project get -i <projectId>",
    "  webrobot project schedule-get -i <projectId>",
    "  webrobot category list",
    "  webrobot agent list -c <categoryId>",
    "  webrobot dataset list",
    "  webrobot dataset get -d <datasetId>",
    "  webrobot package exportall -f /tmp/export.zip",
    "",
    "Opzioni globali: -h / --help, -V / --version (help anche per gruppo: webrobot project --help)"
  ),
  subcommands = Array(
    classOf[RunProjectCommand],
    classOf[RunCategoryCommand],
    classOf[RunAgentCommand],
    classOf[RunJobCommand],
    classOf[RunDatasetCommand],
    classOf[RunImportExportCommand]
  )
)
class WebRobotCliCommand extends Runnable {

  override def run(): Unit = ()
}
