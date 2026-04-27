package org.webrobot.cli.commands

import com.fasterxml.jackson.databind.node.JsonNodeFactory
import org.webrobot.cli.openapi.{JsonCliUtil, OpenApiHttp}
import picocli.CommandLine.{Command, Option}

// ── Helpers ───────────────────────────────────────────────────────────────────

private object TaskPaths {
  def base(projectId: String, jobId: String, c: eu.webrobot.openapi.client.ApiClient): String =
    "/webrobot/api/projects/id/" + c.escapeString(projectId) +
      "/jobs/" + c.escapeString(jobId) + "/tasks"

  def byId(projectId: String, jobId: String, taskId: String, c: eu.webrobot.openapi.client.ApiClient): String =
    base(projectId, jobId, c) + "/" + c.escapeString(taskId)
}

// ── Sub-commands ──────────────────────────────────────────────────────────────

@Command(
  name = "list",
  sortOptions = false,
  description = Array("Elenco task del job (GET .../jobs/{jobId}/tasks).")
)
class RunListTaskCommand extends BaseSubCommand {

  @Option(names = Array("-p", "--projectId"), description = Array("project id"), required = true)
  private var projectId: String = ""

  @Option(names = Array("-j", "--jobId"), description = Array("job id"), required = true)
  private var jobId: String = ""

  override def startRun(): Unit = {
    this.init()
    val node = OpenApiHttp.getJson(apiClient(), TaskPaths.base(projectId, jobId, apiClient()))
    if (node != null && node.isArray)
      JsonCliUtil.renderArrayGrid(node, "id", "name", "taskType", "executionStatus", "enabled", "createdAt")
    else JsonCliUtil.printJson(node)
  }
}

@Command(
  name = "get",
  sortOptions = false,
  description = Array("Dettaglio task (GET .../tasks/{taskId}).")
)
class RunGetTaskCommand extends BaseSubCommand {

  @Option(names = Array("-p", "--projectId"), description = Array("project id"), required = true)
  private var projectId: String = ""

  @Option(names = Array("-j", "--jobId"), description = Array("job id"), required = true)
  private var jobId: String = ""

  @Option(names = Array("-t", "--taskId"), description = Array("task id"), required = true)
  private var taskId: String = ""

  override def startRun(): Unit = {
    this.init()
    val node = OpenApiHttp.getJson(apiClient(), TaskPaths.byId(projectId, jobId, taskId, apiClient()))
    JsonCliUtil.printJson(node)
  }
}

@Command(
  name = "add",
  sortOptions = false,
  description = Array("Crea task (POST .../tasks). Usa --json per body completo.")
)
class RunAddTaskCommand extends BaseSubCommand {

  @Option(names = Array("-p", "--projectId"), description = Array("project id"), required = true)
  private var projectId: String = ""

  @Option(names = Array("-j", "--jobId"), description = Array("job id"), required = true)
  private var jobId: String = ""

  @Option(names = Array("-n", "--name"), description = Array("nome task"), required = true)
  private var name: String = ""

  @Option(names = Array("-d", "--description"), description = Array("descrizione"))
  private var description: String = ""

  @Option(names = Array("--task-type"), description = Array("taskType (es. SPARK, BROWSER, PYTHON)"))
  private var taskType: String = ""

  @Option(names = Array("--bot-id"), description = Array("botId (agent id che esegue il task)"))
  private var botId: String = ""

  @Option(names = Array("--output-dataset-id"), description = Array("outputDatasetId"))
  private var outputDatasetId: String = ""

  @Option(names = Array("-e", "--enabled"), description = Array("true|false (default true)"))
  private var enabledStr: String = "true"

  @Option(names = Array("--json"), description = Array("body JSON completo (sovrascrive tutti gli altri flag)"))
  private var jsonBody: String = ""

  override def startRun(): Unit = {
    this.init()
    val om = apiClient().getObjectMapper
    val body = if (jsonBody != null && jsonBody.trim.nonEmpty) {
      om.readTree(jsonBody.trim)
    } else {
      val o = om.createObjectNode()
      o.put("name", name)
      o.put("jobId", jobId)
      if (description != null && description.nonEmpty) o.put("description", description)
      if (taskType != null && taskType.nonEmpty) o.put("taskType", taskType)
      if (botId != null && botId.nonEmpty) o.put("botId", botId)
      if (outputDatasetId != null && outputDatasetId.nonEmpty) o.put("outputDatasetId", outputDatasetId)
      o.put("enabled", java.lang.Boolean.parseBoolean(enabledStr))
      o
    }
    val node = OpenApiHttp.postJson(apiClient(), TaskPaths.base(projectId, jobId, apiClient()), body)
    JsonCliUtil.printJson(node)
  }
}

@Command(
  name = "update",
  sortOptions = false,
  description = Array("Aggiorna task (PUT .../tasks/{taskId}): GET + merge.")
)
class RunUpdateTaskCommand extends BaseSubCommand {

  @Option(names = Array("-p", "--projectId"), description = Array("project id"), required = true)
  private var projectId: String = ""

  @Option(names = Array("-j", "--jobId"), description = Array("job id"), required = true)
  private var jobId: String = ""

  @Option(names = Array("-t", "--taskId"), description = Array("task id"), required = true)
  private var taskId: String = ""

  @Option(names = Array("-n", "--name"), description = Array("nome"))
  private var name: String = ""

  @Option(names = Array("-d", "--description"), description = Array("descrizione"))
  private var description: String = ""

  @Option(names = Array("--task-type"), description = Array("taskType"))
  private var taskType: String = ""

  @Option(names = Array("--bot-id"), description = Array("botId"))
  private var botId: String = ""

  @Option(names = Array("-e", "--enabled"), description = Array("true|false"))
  private var enabledStr: String = ""

  @Option(names = Array("--json"), description = Array("body JSON completo (sovrascrive tutti gli altri flag)"))
  private var jsonBody: String = ""

  override def startRun(): Unit = {
    this.init()
    val path = TaskPaths.byId(projectId, jobId, taskId, apiClient())
    val om = apiClient().getObjectMapper
    val body = if (jsonBody != null && jsonBody.trim.nonEmpty) {
      om.readTree(jsonBody.trim)
    } else {
      val cur = OpenApiHttp.getJson(apiClient(), path)
      val merged = if (cur != null && cur.isObject)
        cur.deepCopy().asInstanceOf[com.fasterxml.jackson.databind.node.ObjectNode]
      else om.createObjectNode()
      if (name != null && name.nonEmpty) merged.put("name", name)
      if (description != null && description.nonEmpty) merged.put("description", description)
      if (taskType != null && taskType.nonEmpty) merged.put("taskType", taskType)
      if (botId != null && botId.nonEmpty) merged.put("botId", botId)
      if (enabledStr != null && enabledStr.trim.nonEmpty)
        merged.put("enabled", java.lang.Boolean.parseBoolean(enabledStr.trim))
      merged
    }
    val node = OpenApiHttp.putJson(apiClient(), path, body)
    JsonCliUtil.printJson(node)
  }
}

@Command(
  name = "delete",
  sortOptions = false,
  description = Array("Elimina task (DELETE .../tasks/{taskId}).")
)
class RunDeleteTaskCommand extends BaseSubCommand {

  @Option(names = Array("-p", "--projectId"), description = Array("project id"), required = true)
  private var projectId: String = ""

  @Option(names = Array("-j", "--jobId"), description = Array("job id"), required = true)
  private var jobId: String = ""

  @Option(names = Array("-t", "--taskId"), description = Array("task id"), required = true)
  private var taskId: String = ""

  override def startRun(): Unit = {
    this.init()
    OpenApiHttp.deleteJson(apiClient(), TaskPaths.byId(projectId, jobId, taskId, apiClient()))
  }
}

@Command(
  name = "start",
  sortOptions = false,
  description = Array("Avvia task (POST .../tasks/{taskId}/start).")
)
class RunStartTaskCommand extends BaseSubCommand {

  @Option(names = Array("-p", "--projectId"), description = Array("project id"), required = true)
  private var projectId: String = ""

  @Option(names = Array("-j", "--jobId"), description = Array("job id"), required = true)
  private var jobId: String = ""

  @Option(names = Array("-t", "--taskId"), description = Array("task id"), required = true)
  private var taskId: String = ""

  override def startRun(): Unit = {
    this.init()
    val path = TaskPaths.byId(projectId, jobId, taskId, apiClient()) + "/start"
    val node = OpenApiHttp.postJson(apiClient(), path, JsonNodeFactory.instance.objectNode())
    JsonCliUtil.printJson(node)
  }
}

@Command(
  name = "stop",
  sortOptions = false,
  description = Array("Ferma task (POST .../tasks/{taskId}/stop).")
)
class RunStopTaskCommand extends BaseSubCommand {

  @Option(names = Array("-p", "--projectId"), description = Array("project id"), required = true)
  private var projectId: String = ""

  @Option(names = Array("-j", "--jobId"), description = Array("job id"), required = true)
  private var jobId: String = ""

  @Option(names = Array("-t", "--taskId"), description = Array("task id"), required = true)
  private var taskId: String = ""

  override def startRun(): Unit = {
    this.init()
    val path = TaskPaths.byId(projectId, jobId, taskId, apiClient()) + "/stop"
    val node = OpenApiHttp.postJson(apiClient(), path, JsonNodeFactory.instance.objectNode())
    JsonCliUtil.printJson(node)
  }
}

@Command(
  name = "status",
  sortOptions = false,
  description = Array("Stato task (GET .../tasks/{taskId}/status).")
)
class RunTaskStatusCommand extends BaseSubCommand {

  @Option(names = Array("-p", "--projectId"), description = Array("project id"), required = true)
  private var projectId: String = ""

  @Option(names = Array("-j", "--jobId"), description = Array("job id"), required = true)
  private var jobId: String = ""

  @Option(names = Array("-t", "--taskId"), description = Array("task id"), required = true)
  private var taskId: String = ""

  override def startRun(): Unit = {
    this.init()
    val path = TaskPaths.byId(projectId, jobId, taskId, apiClient()) + "/status"
    val node = OpenApiHttp.getJson(apiClient(), path)
    JsonCliUtil.printJson(node)
  }
}

@Command(
  name = "metrics",
  sortOptions = false,
  description = Array("Metriche task (GET .../tasks/{taskId}/metrics).")
)
class RunTaskMetricsCommand extends BaseSubCommand {

  @Option(names = Array("-p", "--projectId"), description = Array("project id"), required = true)
  private var projectId: String = ""

  @Option(names = Array("-j", "--jobId"), description = Array("job id"), required = true)
  private var jobId: String = ""

  @Option(names = Array("-t", "--taskId"), description = Array("task id"), required = true)
  private var taskId: String = ""

  override def startRun(): Unit = {
    this.init()
    val path = TaskPaths.byId(projectId, jobId, taskId, apiClient()) + "/metrics"
    val node = OpenApiHttp.getJson(apiClient(), path)
    JsonCliUtil.printJson(node)
  }
}

// ── Top-level group ───────────────────────────────────────────────────────────

@Command(
  name = "task",
  mixinStandardHelpOptions = true,
  sortOptions = false,
  description = Array("Task di un job (REST .../jobs/{jobId}/tasks/...). Richiede sempre -p/--projectId e -j/--jobId."),
  subcommands = Array(
    classOf[RunListTaskCommand],
    classOf[RunGetTaskCommand],
    classOf[RunAddTaskCommand],
    classOf[RunUpdateTaskCommand],
    classOf[RunDeleteTaskCommand],
    classOf[RunStartTaskCommand],
    classOf[RunStopTaskCommand],
    classOf[RunTaskStatusCommand],
    classOf[RunTaskMetricsCommand]
  )
)
class RunTaskCommand extends Runnable {

  def run(): Unit = {
    System.err.println(
      "Uso: webrobot task <sottocomando>. Sottocomandi: list | get | add | update | delete | start | stop | status | metrics"
    )
    System.err.println("Esempio: webrobot task list -p <projectId> -j <jobId>")
  }
}
