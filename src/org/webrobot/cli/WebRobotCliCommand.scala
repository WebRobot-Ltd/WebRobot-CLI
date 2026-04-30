package org.webrobot.cli

import picocli.CommandLine.Command
import org.webrobot.cli.commands.{
  RunAdminCommand,
  RunAgentCommand,
  RunAiProvidersCommand,
  RunAuthCommand,
  RunBillingCommand,
  RunCategoryCommand,
  RunCloudCommand,
  RunCloudCredentialsCommand,
  RunDatasetCommand,
  RunDatasetLegacyCommand,
  RunExecutionCommand,
  RunImportExportCommand,
  RunJobCommand,
  RunManifestCommand,
  RunPipelineCommand,
  RunPluginCommand,
  RunProjectCommand,
  RunPythonExtCommand,
  RunTaskCommand,
  RunWizardCommand
}

/** Radice Picocli — allineata all'OpenAPI https://api.webrobot.eu/api/openapi.json (138 path). */
@Command(
  name = "webrobot",
  mixinStandardHelpOptions = true,
  version = Array("webrobot-cli 0.3"),
  sortOptions = false,
  description = Array(
    "CLI WebRobot — allineata all'API https://api.webrobot.eu (spec OpenAPI 138 path).",
    "Endpoint: chiave `api_endpoint` in config.cfg (default https://api.webrobot.eu).",
    "Auth: `apikey` (X-API-Key) oppure `jwt` (Bearer) in config.cfg."
  ),
  footer = Array(
    "",
    "Gruppi disponibili:",
    "  Wizard:            wizard agent | wizard pipeline",
    "  Manifest:          manifest | pipeline",
    "  ETL Core:          project | category | agent | job | task | execution",
    "  Dati:              dataset | datasets-legacy | cloud-credentials",
    "  Identità & accesso:auth | billing",
    "  Infrastruttura:    cloud | admin",
    "  AI & estensioni:   ai-providers | python-ext",
    "  Plugin factory:    plugin new | plugin add stage | plugin add resolver | plugin add action",
    "  Pacchetti:         package",
    "",
    "Esempi rapidi:",
    "  webrobot project list",
    "  webrobot job execute -p <projectId> -j <jobId>",
    "  webrobot task list -p <projectId> -j <jobId>",
    "  webrobot cloud-credentials list --provider hetzner",
    "  webrobot auth me",
    "  webrobot cloud spark-info",
    "  webrobot admin system-logs --level ERROR --tail 100",
    "  webrobot python-ext list -a <agentId>",
    "  webrobot ai-providers list",
    "",
    "Per help su un gruppo: webrobot <gruppo> --help"
  ),
  subcommands = Array(
    // Wizard
    classOf[RunWizardCommand],
    // Manifest
    classOf[RunManifestCommand],
    classOf[RunPipelineCommand],
    // ETL core
    classOf[RunProjectCommand],
    classOf[RunCategoryCommand],
    classOf[RunAgentCommand],
    classOf[RunJobCommand],
    classOf[RunTaskCommand],
    classOf[RunExecutionCommand],
    // Dati
    classOf[RunDatasetCommand],
    classOf[RunDatasetLegacyCommand],
    classOf[RunCloudCredentialsCommand],
    // Identità & accesso
    classOf[RunAuthCommand],
    classOf[RunBillingCommand],
    // Infrastruttura
    classOf[RunCloudCommand],
    classOf[RunAdminCommand],
    // AI & estensioni
    classOf[RunAiProvidersCommand],
    classOf[RunPythonExtCommand],
    // Pacchetti
    classOf[RunImportExportCommand],
    // Plugin factory
    classOf[RunPluginCommand]
  )
)
class WebRobotCliCommand extends Runnable {

  override def run(): Unit = ()
}
