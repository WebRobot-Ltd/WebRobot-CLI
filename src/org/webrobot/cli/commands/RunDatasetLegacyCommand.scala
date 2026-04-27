package org.webrobot.cli.commands

import org.webrobot.cli.openapi.{JsonCliUtil, OpenApiHttp}
import picocli.CommandLine.{Command, Option}

@Command(
  name = "list",
  sortOptions = false,
  description = Array("Elenco dataset legacy (GET /webrobot/api/datasets-legacy/datasets).")
)
class RunListLegacyDatasetCommand extends BaseSubCommand {

  override def startRun(): Unit = {
    this.init()
    val node = OpenApiHttp.getJson(apiClient(), "/webrobot/api/datasets-legacy/datasets")
    if (node != null && node.isArray)
      JsonCliUtil.renderArrayGrid(node, "id", "name", "projectId", "botId", "createdAt")
    else JsonCliUtil.printJson(node)
  }
}

@Command(
  name = "get",
  sortOptions = false,
  description = Array("Dataset legacy per id (GET /webrobot/api/datasets-legacy/{projectId}/{botId}/{datasetId}).")
)
class RunGetLegacyDatasetCommand extends BaseSubCommand {

  @Option(names = Array("-p", "--projectId"), description = Array("project id"), required = true)
  private var projectId: String = ""

  @Option(names = Array("--bot-id"), description = Array("bot/agent id"), required = true)
  private var botId: String = ""

  @Option(names = Array("-d", "--datasetId"), description = Array("dataset id"), required = true)
  private var datasetId: String = ""

  override def startRun(): Unit = {
    this.init()
    val path = "/webrobot/api/datasets-legacy/" +
      apiClient().escapeString(projectId) + "/" +
      apiClient().escapeString(botId) + "/" +
      apiClient().escapeString(datasetId)
    val node = OpenApiHttp.getJson(apiClient(), path)
    JsonCliUtil.printJson(node)
  }
}

@Command(
  name = "delete",
  sortOptions = false,
  description = Array("Elimina dataset legacy (DELETE /webrobot/api/datasets-legacy/{projectId}/{botId}/{datasetId}).")
)
class RunDeleteLegacyDatasetCommand extends BaseSubCommand {

  @Option(names = Array("-p", "--projectId"), description = Array("project id"), required = true)
  private var projectId: String = ""

  @Option(names = Array("--bot-id"), description = Array("bot/agent id"), required = true)
  private var botId: String = ""

  @Option(names = Array("-d", "--datasetId"), description = Array("dataset id"), required = true)
  private var datasetId: String = ""

  override def startRun(): Unit = {
    this.init()
    val path = "/webrobot/api/datasets-legacy/" +
      apiClient().escapeString(projectId) + "/" +
      apiClient().escapeString(botId) + "/" +
      apiClient().escapeString(datasetId)
    OpenApiHttp.deleteJson(apiClient(), path)
  }
}

@Command(
  name = "versions",
  sortOptions = false,
  description = Array("Versioni dataset legacy (GET /webrobot/api/datasets-legacy/{datasetId}/versions).")
)
class RunLegacyDatasetVersionsCommand extends BaseSubCommand {

  @Option(names = Array("-d", "--datasetId"), description = Array("dataset id"), required = true)
  private var datasetId: String = ""

  override def startRun(): Unit = {
    this.init()
    val path = "/webrobot/api/datasets-legacy/" + apiClient().escapeString(datasetId) + "/versions"
    val node = OpenApiHttp.getJson(apiClient(), path)
    if (node != null && node.isArray)
      JsonCliUtil.renderArrayGrid(node, "id", "version", "datasetId", "createdAt")
    else JsonCliUtil.printJson(node)
  }
}

@Command(
  name = "status",
  sortOptions = false,
  description = Array("Stato dataset legacy (GET /webrobot/api/datasets-legacy/datasets/{datasetId}/status).")
)
class RunLegacyDatasetStatusCommand extends BaseSubCommand {

  @Option(names = Array("-d", "--datasetId"), description = Array("dataset id"), required = true)
  private var datasetId: String = ""

  override def startRun(): Unit = {
    this.init()
    val path = "/webrobot/api/datasets-legacy/datasets/" + apiClient().escapeString(datasetId) + "/status"
    val node = OpenApiHttp.getJson(apiClient(), path)
    JsonCliUtil.printJson(node)
  }
}

@Command(
  name = "input-url",
  sortOptions = false,
  description = Array("URL file input dataset legacy (GET /webrobot/api/datasets-legacy/{projectId}/{botId}/{datasetId}/input/url).")
)
class RunLegacyDatasetInputUrlCommand extends BaseSubCommand {

  @Option(names = Array("-p", "--projectId"), description = Array("project id"), required = true)
  private var projectId: String = ""

  @Option(names = Array("--bot-id"), description = Array("bot/agent id"), required = true)
  private var botId: String = ""

  @Option(names = Array("-d", "--datasetId"), description = Array("dataset id"), required = true)
  private var datasetId: String = ""

  override def startRun(): Unit = {
    this.init()
    val path = "/webrobot/api/datasets-legacy/" +
      apiClient().escapeString(projectId) + "/" +
      apiClient().escapeString(botId) + "/" +
      apiClient().escapeString(datasetId) + "/input/url"
    val node = OpenApiHttp.getJson(apiClient(), path)
    JsonCliUtil.printJson(node)
  }
}

@Command(
  name = "datasets-legacy",
  mixinStandardHelpOptions = true,
  sortOptions = false,
  description = Array(
    "Dataset legacy (REST /webrobot/api/datasets-legacy/...). Compatibilità con le API pre-2024.",
    "Usa `webrobot dataset` per le API dataset correnti."
  ),
  subcommands = Array(
    classOf[RunListLegacyDatasetCommand],
    classOf[RunGetLegacyDatasetCommand],
    classOf[RunDeleteLegacyDatasetCommand],
    classOf[RunLegacyDatasetVersionsCommand],
    classOf[RunLegacyDatasetStatusCommand],
    classOf[RunLegacyDatasetInputUrlCommand]
  )
)
class RunDatasetLegacyCommand extends Runnable {

  def run(): Unit = {
    System.err.println(
      "Uso: webrobot datasets-legacy <sottocomando>. Sottocomandi: list | get | delete | versions | status | input-url"
    )
    System.err.println("Esempio: webrobot datasets-legacy list")
  }
}
