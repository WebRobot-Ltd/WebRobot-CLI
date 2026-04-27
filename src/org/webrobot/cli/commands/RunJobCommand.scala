package org.webrobot.cli.commands

import com.fasterxml.jackson.databind.node.{JsonNodeFactory, ObjectNode}
import eu.webrobot.openapi.client.model.JobDto
import org.webrobot.cli.openapi.{JsonCliUtil, OpenApiHttp}
import org.webrobot.cli.utils.DataGrid
import picocli.CommandLine.{Command, Option}

@Command(
  name = "list",
  sortOptions = false,
  description = Array("Elenco job del progetto (GET .../projects/id/{projectId}/jobs)."),
  footer = Array()
)
class RunListJobCommand extends BaseSubCommand {

  @Option(names = Array("-p", "--projectId"), description = Array("project id"), required = true)
  private var projectId: String = ""

  override def startRun(): Unit = {
    this.init()
    val path =
      "/webrobot/api/projects/id/" + apiClient().escapeString(projectId) + "/jobs"
    val node = OpenApiHttp.getJson(apiClient(), path)
    if (node != null && node.isArray)
      JsonCliUtil.renderArrayGrid(
        node,
        "id",
        "name",
        "agentId",
        "inputDatasetId",
        "executionStatus",
        "enabled"
      )
    else JsonCliUtil.printJson(node)
  }
}

@Command(
  name = "delete",
  sortOptions = false,
  description = Array("Rimuove job (DELETE .../jobs/{jobId})."),
  footer = Array()
)
class RunDeleteJobCommand extends BaseSubCommand {

  @Option(names = Array("-p", "--projectId"), description = Array("project id"), required = true)
  private var projectId: String = ""

  @Option(names = Array("-j", "--jobId"), description = Array("job id"), required = true)
  private var jobId: String = ""

  override def startRun(): Unit = {
    this.init()
    val path =
      "/webrobot/api/projects/id/" + apiClient().escapeString(projectId) + "/jobs/" + apiClient().escapeString(jobId)
    OpenApiHttp.deleteJson(apiClient(), path)
  }
}

@Command(
  name = "get",
  sortOptions = false,
  description = Array("Dettaglio job (GET .../jobs/{jobId}), JSON su stdout."),
  footer = Array()
)
class RunGetJobCommand extends BaseSubCommand {

  @Option(names = Array("-p", "--projectId"), description = Array("project id"), required = true)
  private var projectId: String = ""

  @Option(names = Array("-j", "--jobId"), description = Array("job id"), required = true)
  private var jobId: String = ""

  override def startRun(): Unit = {
    this.init()
    val path =
      "/webrobot/api/projects/id/" + apiClient().escapeString(projectId) + "/jobs/" + apiClient().escapeString(jobId)
    val node = OpenApiHttp.getJson(apiClient(), path)
    JsonCliUtil.printJson(node)
  }
}

@Command(
  name = "add",
  sortOptions = false,
  description = Array("Aggiunge job (POST .../jobs)."),
  footer = Array(),
  subcommands = Array()
)
class RunAddJobCommand extends BaseSubCommand {

  @Option(names = Array("-p", "--projectId"), description = Array("project id"), required = true)
  private var projectId: String = ""

  @Option(names = Array("-n", "--name"), description = Array("nome job"), required = true)
  private var name: String = ""

  @Option(names = Array("-d", "--description"), description = Array("descrizione"))
  private var description: String = ""

  @Option(names = Array("-a", "--agentId"), description = Array("agent id"), required = true)
  private var agentId: String = ""

  @Option(names = Array("-i", "--inputDatasetId"), description = Array("dataset di input"), required = true)
  private var inputDatasetId: String = ""

  @Option(names = Array("--cloud-credential-id"), description = Array("JobDto.cloudCredentialId (opzionale)"))
  private var cloudCredentialId: String = ""

  @Option(names = Array("--job-type"), description = Array("BATCH o STREAMING (JobDto.jobType, opzionale)"))
  private var jobType: String = ""

  override def startRun(): Unit = {
    this.init()
    val dto = new JobDto()
    dto.setProjectId(projectId)
    dto.setName(name)
    if (description != null) dto.setDescription(description)
    dto.setAgentId(agentId)
    dto.setInputDatasetId(inputDatasetId)
    if (cloudCredentialId != null && cloudCredentialId.nonEmpty) dto.setCloudCredentialId(cloudCredentialId)
    if (jobType != null && jobType.trim.nonEmpty) dto.setJobType(eu.webrobot.openapi.client.model.JobDto.JobTypeEnum.fromValue(jobType.trim.toUpperCase))
    val path =
      "/webrobot/api/projects/id/" + apiClient().escapeString(projectId) + "/jobs"
    val node = OpenApiHttp.postJson(apiClient(), path, dto)
    JsonCliUtil.printJson(node)
  }
}

@Command(
  name = "update",
  sortOptions = false,
  description = Array(
    "Aggiorna job (PUT .../jobs/{jobId}): GET stato corrente poi merge (evita di azzerare enabled/agent/dataset non passati)."
  ),
  footer = Array()
)
class RunUpdateJobCommand extends BaseSubCommand {

  @Option(names = Array("-p", "--projectId"), description = Array("project id"), required = true)
  private var projectId: String = ""

  @Option(names = Array("-j", "--jobId"), description = Array("job id"), required = true)
  private var jobId: String = ""

  @Option(names = Array("-n", "--name"), description = Array("nome"), required = true)
  private var name: String = ""

  @Option(names = Array("-d", "--description"), description = Array("descrizione (opzionale; se omesso resta il valore sul server)"))
  private var description: String = ""

  @Option(names = Array("-a", "--agentId"), description = Array("agent id (opzionale; solo se si vuole cambiare)"))
  private var agentId: String = ""

  @Option(names = Array("-i", "--inputDatasetId"), description = Array("dataset di input (opzionale; solo se si vuole cambiare)"))
  private var inputDatasetId: String = ""

  @Option(names = Array("--cloud-credential-id"), description = Array("cloudCredentialId (opzionale)"))
  private var cloudCredentialId: String = ""

  @Option(names = Array("-e", "--enabled"), description = Array("true|false (opzionale; se omesso resta il valore sul server)"))
  private var enabledStr: String = ""

  @Option(names = Array("--job-type"), description = Array("BATCH o STREAMING (opzionale)"))
  private var jobType: String = ""

  override def startRun(): Unit = {
    this.init()
    val path =
      "/webrobot/api/projects/id/" + apiClient().escapeString(projectId) + "/jobs/" + apiClient().escapeString(jobId)
    val cur = OpenApiHttp.getJson(apiClient(), path)
    if (cur == null || cur.isNull || !cur.isObject)
      throw new IllegalStateException("Impossibile leggere il job prima dell'update: " + cur)
    val merged = cur.deepCopy().asInstanceOf[ObjectNode]
    merged.put("id", jobId)
    merged.put("projectId", projectId)
    merged.put("name", name)
    if (description != null) merged.put("description", description)
    if (agentId != null && agentId.nonEmpty) merged.put("agentId", agentId)
    if (inputDatasetId != null && inputDatasetId.nonEmpty) merged.put("inputDatasetId", inputDatasetId)
    if (cloudCredentialId != null && cloudCredentialId.nonEmpty) merged.put("cloudCredentialId", cloudCredentialId)
    if (enabledStr != null && enabledStr.trim.nonEmpty)
      merged.put("enabled", java.lang.Boolean.parseBoolean(enabledStr.trim))
    if (jobType != null && jobType.trim.nonEmpty)
      merged.put("jobType", jobType.trim.toUpperCase)
    OpenApiHttp.putJson(apiClient(), path, merged)
  }
}

@Command(
  name = "execute",
  sortOptions = false,
  description = Array("Esegue job (POST .../execute). Body JSON opzionale."),
  footer = Array()
)
class RunExecuteJobCommand extends BaseSubCommand {

  @Option(names = Array("-p", "--projectId"), description = Array("project id"), required = true)
  private var projectId: String = ""

  @Option(names = Array("-j", "--jobId"), description = Array("job id"), required = true)
  private var jobId: String = ""

  @Option(names = Array("-b", "--bodyJson"), description = Array("JSON body (default {})"))
  private var bodyJson: String = ""

  override def startRun(): Unit = {
    this.init()
    val path =
      "/webrobot/api/projects/id/" + apiClient().escapeString(projectId) + "/jobs/" + apiClient().escapeString(
        jobId
      ) + "/execute"
    val body =
      if (bodyJson != null && bodyJson.trim.nonEmpty)
        apiClient().getObjectMapper.readTree(bodyJson)
      else JsonNodeFactory.instance.objectNode()
    val node = OpenApiHttp.postJson(apiClient(), path, body)
    JsonCliUtil.printJson(node)
  }
}

@Command(
  name = "stop",
  sortOptions = false,
  description = Array("Ferma job (POST .../stop)."),
  footer = Array()
)
class RunStopJobCommand extends BaseSubCommand {

  @Option(names = Array("-p", "--projectId"), description = Array("project id"), required = true)
  private var projectId: String = ""

  @Option(names = Array("-j", "--jobId"), description = Array("job id"), required = true)
  private var jobId: String = ""

  override def startRun(): Unit = {
    this.init()
    val path =
      "/webrobot/api/projects/id/" + apiClient().escapeString(projectId) + "/jobs/" + apiClient().escapeString(
        jobId
      ) + "/stop"
    val node = OpenApiHttp.postJson(apiClient(), path, JsonNodeFactory.instance.objectNode())
    JsonCliUtil.printJson(node)
  }
}

@Command(
  name = "logs",
  sortOptions = false,
  description = Array("Log job (GET .../jobs/{jobId}/logs); query opzionali come in OpenAPI."),
  footer = Array()
)
class RunJobLogsCommand extends BaseSubCommand {

  @Option(names = Array("-p", "--projectId"), description = Array("project id"), required = true)
  private var projectId: String = ""

  @Option(names = Array("-j", "--jobId"), description = Array("job id"), required = true)
  private var jobId: String = ""

  @Option(names = Array("--task-id"), description = Array("query taskId (int64)"))
  private var taskId: String = ""

  @Option(names = Array("--pod-type"), description = Array("query podType"))
  private var podType: String = ""

  @Option(names = Array("--executor-index"), description = Array("query executorIndex (int)"))
  private var executorIndex: String = ""

  @Option(names = Array("--pod-name"), description = Array("query podName"))
  private var podName: String = ""

  @Option(names = Array("--tail"), description = Array("query tail (righe)"))
  private var tail: String = ""

  override def startRun(): Unit = {
    this.init()
    val path =
      "/webrobot/api/projects/id/" + apiClient().escapeString(projectId) + "/jobs/" + apiClient().escapeString(
        jobId
      ) + "/logs"
    val tuples = Seq(
      scala.Option(taskId).filter(_.nonEmpty).map("taskId" -> _.asInstanceOf[AnyRef]),
      scala.Option(podType).filter(_.nonEmpty).map("podType" -> _.asInstanceOf[AnyRef]),
      scala.Option(executorIndex).filter(_.nonEmpty).map("executorIndex" -> _.asInstanceOf[AnyRef]),
      scala.Option(podName).filter(_.nonEmpty).map("podName" -> _.asInstanceOf[AnyRef]),
      scala.Option(tail).filter(_.nonEmpty).map("tail" -> _.asInstanceOf[AnyRef])
    ).flatten
    val qp = OpenApiHttp.pairs(apiClient(), tuples: _*)
    val node =
      if (tuples.isEmpty) OpenApiHttp.getJson(apiClient(), path)
      else OpenApiHttp.getJson(apiClient(), path, qp)
    JsonCliUtil.printJson(node)
  }
}

@Command(
  name = "metrics",
  sortOptions = false,
  description = Array("Metriche job (GET .../jobs/{jobId}/metrics)."),
  footer = Array()
)
class RunJobMetricsCommand extends BaseSubCommand {

  @Option(names = Array("-p", "--projectId"), description = Array("project id"), required = true)
  private var projectId: String = ""

  @Option(names = Array("-j", "--jobId"), description = Array("job id"), required = true)
  private var jobId: String = ""

  override def startRun(): Unit = {
    this.init()
    val path =
      "/webrobot/api/projects/id/" + apiClient().escapeString(projectId) + "/jobs/" + apiClient().escapeString(jobId) + "/metrics"
    val node = OpenApiHttp.getJson(apiClient(), path)
    JsonCliUtil.printJson(node)
  }
}

@Command(
  name = "job",
  mixinStandardHelpOptions = true,
  sortOptions = false,
  description = Array("Job (REST OpenAPI /webrobot/api/projects/.../jobs/...)."),
  footer = Array(),
  subcommands = Array(
    classOf[RunListJobCommand],
    classOf[RunGetJobCommand],
    classOf[RunAddJobCommand],
    classOf[RunUpdateJobCommand],
    classOf[RunDeleteJobCommand],
    classOf[RunExecuteJobCommand],
    classOf[RunStopJobCommand],
    classOf[RunJobLogsCommand],
    classOf[RunJobMetricsCommand]
  )
)
class RunJobCommand extends Runnable {

  def run(): Unit = {
    System.err.println(
      "Uso: webrobot job <sottocomando>. Sottocomandi: list | get | add | update | delete | execute | stop | logs | metrics"
    )
    System.err.println("Esempio: webrobot job list -p <projectId>")
  }
}
