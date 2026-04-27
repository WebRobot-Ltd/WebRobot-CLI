package org.webrobot.cli.commands

import org.webrobot.cli.openapi.{JsonCliUtil, OpenApiHttp}
import picocli.CommandLine.{Command, Option}

private object ExecutionPaths {
  def base(projectId: String, jobId: String, executionId: String, c: eu.webrobot.openapi.client.ApiClient): String =
    "/webrobot/api/projects/id/" + c.escapeString(projectId) +
      "/jobs/" + c.escapeString(jobId) +
      "/executions/" + c.escapeString(executionId)
}

@Command(
  name = "status",
  sortOptions = false,
  description = Array("Stato esecuzione (GET .../executions/{executionId}/status).")
)
class RunExecutionStatusCommand extends BaseSubCommand {

  @Option(names = Array("-p", "--projectId"), description = Array("project id"), required = true)
  private var projectId: String = ""

  @Option(names = Array("-j", "--jobId"), description = Array("job id"), required = true)
  private var jobId: String = ""

  @Option(names = Array("-e", "--executionId"), description = Array("execution id"), required = true)
  private var executionId: String = ""

  override def startRun(): Unit = {
    this.init()
    val path = ExecutionPaths.base(projectId, jobId, executionId, apiClient()) + "/status"
    val node = OpenApiHttp.getJson(apiClient(), path)
    JsonCliUtil.printJson(node)
  }
}

@Command(
  name = "logs",
  sortOptions = false,
  description = Array("Log esecuzione (GET .../executions/{executionId}/logs).")
)
class RunExecutionLogsCommand extends BaseSubCommand {

  @Option(names = Array("-p", "--projectId"), description = Array("project id"), required = true)
  private var projectId: String = ""

  @Option(names = Array("-j", "--jobId"), description = Array("job id"), required = true)
  private var jobId: String = ""

  @Option(names = Array("-e", "--executionId"), description = Array("execution id"), required = true)
  private var executionId: String = ""

  override def startRun(): Unit = {
    this.init()
    val path = ExecutionPaths.base(projectId, jobId, executionId, apiClient()) + "/logs"
    val node = OpenApiHttp.getJson(apiClient(), path)
    JsonCliUtil.printJson(node)
  }
}

@Command(
  name = "cancel",
  sortOptions = false,
  description = Array("Cancella/annulla esecuzione (DELETE .../executions/{executionId}).")
)
class RunCancelExecutionCommand extends BaseSubCommand {

  @Option(names = Array("-p", "--projectId"), description = Array("project id"), required = true)
  private var projectId: String = ""

  @Option(names = Array("-j", "--jobId"), description = Array("job id"), required = true)
  private var jobId: String = ""

  @Option(names = Array("-e", "--executionId"), description = Array("execution id"), required = true)
  private var executionId: String = ""

  override def startRun(): Unit = {
    this.init()
    OpenApiHttp.deleteJson(apiClient(), ExecutionPaths.base(projectId, jobId, executionId, apiClient()))
  }
}

@Command(
  name = "execution",
  mixinStandardHelpOptions = true,
  sortOptions = false,
  description = Array("Esecuzioni job (REST .../jobs/{jobId}/executions/...). Richiede -p, -j, -e."),
  subcommands = Array(
    classOf[RunExecutionStatusCommand],
    classOf[RunExecutionLogsCommand],
    classOf[RunCancelExecutionCommand]
  )
)
class RunExecutionCommand extends Runnable {

  def run(): Unit = {
    System.err.println("Uso: webrobot execution <sottocomando>. Sottocomandi: status | logs | cancel")
    System.err.println("Esempio: webrobot execution status -p <projectId> -j <jobId> -e <executionId>")
  }
}
