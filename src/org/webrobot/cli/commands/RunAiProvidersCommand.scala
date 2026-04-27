package org.webrobot.cli.commands

import org.webrobot.cli.openapi.{JsonCliUtil, OpenApiHttp}
import picocli.CommandLine.{Command, Option}

@Command(name = "list", sortOptions = false,
  description = Array("Provider AI supportati (GET /webrobot/api/ai-providers/providers)."))
class RunListAiProvidersCommand extends BaseSubCommand {

  override def startRun(): Unit = {
    this.init()
    val node = OpenApiHttp.getJson(apiClient(), "/webrobot/api/ai-providers/providers")
    if (node != null && node.isArray)
      JsonCliUtil.renderArrayGrid(node, "id", "name", "type", "enabled")
    else JsonCliUtil.printJson(node)
  }
}

@Command(name = "models", sortOptions = false,
  description = Array("Modelli disponibili per un provider (GET /webrobot/api/ai-providers/providers/{provider}/models)."))
class RunListAiModelsCommand extends BaseSubCommand {

  @Option(names = Array("--provider"), description = Array("provider (es. openai, huggingface, mistral)"), required = true)
  private var provider: String = ""

  override def startRun(): Unit = {
    this.init()
    val path = "/webrobot/api/ai-providers/providers/" + apiClient().escapeString(provider) + "/models"
    val node = OpenApiHttp.getJson(apiClient(), path)
    if (node != null && node.isArray)
      JsonCliUtil.renderArrayGrid(node, "id", "name", "type", "contextLength")
    else JsonCliUtil.printJson(node)
  }
}

@Command(name = "training-start", sortOptions = false,
  description = Array("Avvia training (POST /webrobot/api/ai-providers/providers/{provider}/training). Usa --json per body completo."))
class RunStartTrainingCommand extends BaseSubCommand {

  @Option(names = Array("--provider"), description = Array("provider"), required = true)
  private var provider: String = ""

  @Option(names = Array("--json"), description = Array("body JSON (TrainingRequestBean)"), required = true)
  private var jsonBody: String = ""

  override def startRun(): Unit = {
    this.init()
    val path = "/webrobot/api/ai-providers/providers/" + apiClient().escapeString(provider) + "/training"
    val node = OpenApiHttp.postJson(apiClient(), path, apiClient().getObjectMapper.readTree(jsonBody.trim))
    JsonCliUtil.printJson(node)
  }
}

@Command(name = "training-status", sortOptions = false,
  description = Array("Stato training (GET /webrobot/api/ai-providers/providers/{provider}/training/{jobId}/status)."))
class RunTrainingStatusCommand extends BaseSubCommand {

  @Option(names = Array("--provider"), description = Array("provider"), required = true)
  private var provider: String = ""

  @Option(names = Array("-j", "--jobId"), description = Array("training job id"), required = true)
  private var jobId: String = ""

  override def startRun(): Unit = {
    this.init()
    val path = "/webrobot/api/ai-providers/providers/" +
      apiClient().escapeString(provider) + "/training/" + apiClient().escapeString(jobId) + "/status"
    JsonCliUtil.printJson(OpenApiHttp.getJson(apiClient(), path))
  }
}

@Command(name = "training-logs", sortOptions = false,
  description = Array("Log training (GET /webrobot/api/ai-providers/providers/{provider}/training/{jobId}/logs)."))
class RunTrainingLogsCommand extends BaseSubCommand {

  @Option(names = Array("--provider"), description = Array("provider"), required = true)
  private var provider: String = ""

  @Option(names = Array("-j", "--jobId"), description = Array("training job id"), required = true)
  private var jobId: String = ""

  override def startRun(): Unit = {
    this.init()
    val path = "/webrobot/api/ai-providers/providers/" +
      apiClient().escapeString(provider) + "/training/" + apiClient().escapeString(jobId) + "/logs"
    JsonCliUtil.printJson(OpenApiHttp.getJson(apiClient(), path))
  }
}

@Command(name = "training-cancel", sortOptions = false,
  description = Array("Cancella training (DELETE /webrobot/api/ai-providers/providers/{provider}/training/{jobId})."))
class RunCancelTrainingCommand extends BaseSubCommand {

  @Option(names = Array("--provider"), description = Array("provider"), required = true)
  private var provider: String = ""

  @Option(names = Array("-j", "--jobId"), description = Array("training job id"), required = true)
  private var jobId: String = ""

  override def startRun(): Unit = {
    this.init()
    val path = "/webrobot/api/ai-providers/providers/" +
      apiClient().escapeString(provider) + "/training/" + apiClient().escapeString(jobId)
    OpenApiHttp.deleteJson(apiClient(), path)
  }
}

@Command(name = "cost-estimate", sortOptions = false,
  description = Array("Stima costo training (POST /webrobot/api/ai-providers/providers/{provider}/cost-estimate)."))
class RunCostEstimateCommand extends BaseSubCommand {

  @Option(names = Array("--provider"), description = Array("provider"), required = true)
  private var provider: String = ""

  @Option(names = Array("--json"), description = Array("body JSON"), required = true)
  private var jsonBody: String = ""

  override def startRun(): Unit = {
    this.init()
    val path = "/webrobot/api/ai-providers/providers/" + apiClient().escapeString(provider) + "/cost-estimate"
    val node = OpenApiHttp.postJson(apiClient(), path, apiClient().getObjectMapper.readTree(jsonBody.trim))
    JsonCliUtil.printJson(node)
  }
}

@Command(name = "datasets-upload", sortOptions = false,
  description = Array("Upload dataset per training (POST /webrobot/api/ai-providers/providers/{provider}/datasets)."))
class RunAiDatasetUploadCommand extends BaseSubCommand {

  @Option(names = Array("--provider"), description = Array("provider"), required = true)
  private var provider: String = ""

  @Option(names = Array("--json"), description = Array("body JSON (DatasetUploadRequest)"), required = true)
  private var jsonBody: String = ""

  override def startRun(): Unit = {
    this.init()
    val path = "/webrobot/api/ai-providers/providers/" + apiClient().escapeString(provider) + "/datasets"
    val node = OpenApiHttp.postJson(apiClient(), path, apiClient().getObjectMapper.readTree(jsonBody.trim))
    JsonCliUtil.printJson(node)
  }
}

@Command(name = "huggingface-publish", sortOptions = false,
  description = Array("Pubblica modello su Hugging Face (POST /webrobot/api/ai-providers/providers/huggingface/models/publish)."))
class RunHuggingFacePublishCommand extends BaseSubCommand {

  @Option(names = Array("--json"), description = Array("body JSON (ModelPublishRequest)"), required = true)
  private var jsonBody: String = ""

  override def startRun(): Unit = {
    this.init()
    val path = "/webrobot/api/ai-providers/providers/huggingface/models/publish"
    val node = OpenApiHttp.postJson(apiClient(), path, apiClient().getObjectMapper.readTree(jsonBody.trim))
    JsonCliUtil.printJson(node)
  }
}

@Command(
  name = "ai-providers",
  mixinStandardHelpOptions = true,
  sortOptions = false,
  description = Array("AI Providers & Training (REST /webrobot/api/ai-providers/...)."),
  subcommands = Array(
    classOf[RunListAiProvidersCommand],
    classOf[RunListAiModelsCommand],
    classOf[RunStartTrainingCommand],
    classOf[RunTrainingStatusCommand],
    classOf[RunTrainingLogsCommand],
    classOf[RunCancelTrainingCommand],
    classOf[RunCostEstimateCommand],
    classOf[RunAiDatasetUploadCommand],
    classOf[RunHuggingFacePublishCommand]
  )
)
class RunAiProvidersCommand extends Runnable {

  def run(): Unit = {
    System.err.println("Uso: webrobot ai-providers <sottocomando>.")
    System.err.println(
      "Sottocomandi: list | models | training-start | training-status | training-logs | training-cancel | cost-estimate | datasets-upload | huggingface-publish"
    )
  }
}
