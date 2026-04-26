package org.webrobot.cli.commands

import com.fasterxml.jackson.databind.node.ObjectNode
import eu.webrobot.openapi.client.model.{JobProjectDto, ProjectScheduleRequest}
import org.webrobot.cli.openapi.{JsonCliUtil, OpenApiHttp}
import org.webrobot.cli.utils.DataGrid
import picocli.CommandLine.{Command, Option}

@Command(
  name = "delete",
  sortOptions = false,
  description = Array("Elimina progetto (DELETE /webrobot/api/projects/id/{projectId})."),
  footer = Array()
)
class RunDeleteProjectCommand extends BaseSubCommand {

  @Option(names = Array("-i", "--projectId"), description = Array("project id"), required = true)
  private var projectId: String = ""

  override def startRun(): Unit = {
    this.init()
    val path =
      "/webrobot/api/projects/id/" + apiClient().escapeString(projectId)
    OpenApiHttp.deleteJson(apiClient(), path)
  }
}

@Command(
  name = "list",
  sortOptions = false,
  description = Array("Elenco progetti (GET /webrobot/api/projects)."),
  footer = Array()
)
class RunListProjectCommand extends BaseSubCommand {

  override def startRun(): Unit = {
    this.init()
    val node = OpenApiHttp.getJson(apiClient(), "/webrobot/api/projects")
    if (node != null && node.isArray)
      JsonCliUtil.renderArrayGrid(node, "id", "name", "description", "createdAt", "updatedAt")
    else if (node != null && node.isObject) {
      val dg = new DataGrid("json")
      dg.add(node.toString)
      dg.render
      System.out.println("1 rows in set\n")
    } else System.out.println("Empty set\n")
  }
}

@Command(
  name = "update",
  sortOptions = false,
  description = Array("Aggiorna progetto (PUT /webrobot/api/projects/id/{projectId})."),
  footer = Array()
)
class RunUpdateProjectCommand extends BaseSubCommand {

  @Option(names = Array("-i", "--id"), description = Array("project id"), required = true)
  private var projectId: String = ""

  @Option(names = Array("-n", "--name"), description = Array("nome"), required = true)
  private var projectName: String = ""

  @Option(names = Array("-d", "--description"), description = Array("descrizione"), required = true)
  private var projectDescription: String = ""

  override def startRun(): Unit = {
    this.init()
    val dto = new JobProjectDto()
    dto.setName(projectName)
    dto.setDescription(projectDescription)
    val path =
      "/webrobot/api/projects/id/" + apiClient().escapeString(projectId)
    OpenApiHttp.putJson(apiClient(), path, dto)
    System.out.println("projectID:" + projectId)
  }
}

@Command(
  name = "get",
  sortOptions = false,
  description = Array("Dettaglio progetto per id (GET /webrobot/api/projects/id/{projectId})."),
  footer = Array(),
  subcommands = Array()
)
class RunGetProjectCommand extends BaseSubCommand {

  @Option(
    names = Array("-i", "--projectId", "-n"),
    description = Array("project id (-n resta alias breve per compatibilità)"),
    required = true
  )
  private var projectId: String = ""

  override def startRun(): Unit = {
    this.init()
    val path =
      "/webrobot/api/projects/id/" + apiClient().escapeString(projectId)
    val node = OpenApiHttp.getJson(apiClient(), path)
    val dg = new DataGrid("Id", "Name", "Description", "Created", "Updated")
    dg.add(
      JsonCliUtil.text(node, "id"),
      JsonCliUtil.text(node, "name"),
      JsonCliUtil.text(node, "description"),
      JsonCliUtil.text(node, "createdAt"),
      JsonCliUtil.text(node, "updatedAt")
    )
    if (dg.size > 0) {
      dg.render
      System.out.println(dg.size + " rows in set\n")
    } else System.out.println("Empty set\n")
  }
}

@Command(
  name = "add",
  sortOptions = false,
  description = Array("Crea progetto (POST /webrobot/api/projects)."),
  footer = Array(),
  subcommands = Array()
)
class RunAddProjectCommand extends BaseSubCommand {

  @Option(names = Array("-n", "--name"), description = Array("nome progetto"), required = true)
  private var projectName: String = ""

  @Option(names = Array("-d", "--description"), description = Array("descrizione"), required = true)
  private var projectDescription: String = ""

  override def startRun(): Unit = {
    this.init()
    val dto = new JobProjectDto()
    dto.setName(projectName)
    dto.setDescription(projectDescription)
    val node = OpenApiHttp.postJson(apiClient(), "/webrobot/api/projects", dto)
    JsonCliUtil.printJson(node)
  }
}

@Command(
  name = "schedule-get",
  sortOptions = false,
  description = Array("Schedule ETL progetto (GET /webrobot/api/projects/id/{id}/schedule)."),
  footer = Array()
)
class RunGetProjectScheduleCommand extends BaseSubCommand {

  @Option(names = Array("-i", "--projectId"), description = Array("project id"), required = true)
  private var projectId: String = ""

  override def startRun(): Unit = {
    this.init()
    val path =
      "/webrobot/api/projects/id/" + apiClient().escapeString(projectId) + "/schedule"
    val node = OpenApiHttp.getJson(apiClient(), path)
    JsonCliUtil.printJson(node)
  }
}

@Command(
  name = "schedule-set",
  sortOptions = false,
  description = Array("Imposta schedule ETL (PUT /webrobot/api/projects/id/{id}/schedule)."),
  footer = Array()
)
class RunSetProjectScheduleCommand extends BaseSubCommand {

  @Option(names = Array("-i", "--projectId"), description = Array("project id"), required = true)
  private var projectId: String = ""

  @Option(
    names = Array("-j", "--jobId"),
    description = Array(
      "job id: obbligatorio se -e true (allineato a Jersey: senza jobId lo schedule abilitato non può creare il CronJob)."
    )
  )
  private var jobId: String = ""

  @Option(names = Array("-c", "--cron"), description = Array("cron (default 0 0 * * *)"))
  private var cron: String = "0 0 * * *"

  @Option(names = Array("-e", "--enabled"), description = Array("true|false (default true)"))
  private var enabledStr: String = "true"

  @Option(names = Array("-z", "--timezone"), description = Array("timezone (default UTC)"))
  private var timezone: String = "UTC"

  @Option(
    names = Array("--execution-json"),
    description = Array("JSON oggetto opzionale per il body del POST /execute (merge con i flag --elastic-browser-* se presenti).")
  )
  private var executionJson: String = ""

  @Option(
    names = Array("--elastic-browser-vm-count"),
    description = Array("elasticBrowserVmCount nel body execute (VM Hetzner/Ansible pre-spark).")
  )
  private var elasticBrowserVmCount: String = ""

  @Option(
    names = Array("--elastic-browser-cloud-credential-id"),
    description = Array("elasticBrowserCloudCredentialId (id credenziale cloud salvata).")
  )
  private var elasticBrowserCloudCredentialId: String = ""

  @Option(
    names = Array("--elastic-browser-infra-provider"),
    description = Array("elasticBrowserInfrastructureProvider (es. hetzner).")
  )
  private var elasticBrowserInfraProvider: String = ""

  @Option(
    names = Array("--use-ephemeral-elastic-browser-vms"),
    description = Array("useEphemeralElasticBrowserVms: true|false nel body execute.")
  )
  private var useEphemeralElasticBrowserVms: String = ""

  /** Compose `executionRequestJson` come stringa: merge `--execution-json` + flag dedicati (i flag vincono su chiavi duplicate). */
  private def mergedExecutionRequestJson(): scala.Option[String] = {
    val hasExec =
      executionJson != null && executionJson.trim.nonEmpty
    val hasVm =
      elasticBrowserVmCount != null && elasticBrowserVmCount.trim.nonEmpty
    val hasCred =
      elasticBrowserCloudCredentialId != null && elasticBrowserCloudCredentialId.trim.nonEmpty
    val hasInfra =
      elasticBrowserInfraProvider != null && elasticBrowserInfraProvider.trim.nonEmpty
    val hasEphem =
      useEphemeralElasticBrowserVms != null && useEphemeralElasticBrowserVms.trim.nonEmpty
    if (!hasExec && !hasVm && !hasCred && !hasInfra && !hasEphem) return scala.None

    val om = apiClient().getObjectMapper
    val obj: ObjectNode =
      if (hasExec) {
        val n = om.readTree(executionJson.trim)
        if (n == null || !n.isObject)
          throw new IllegalArgumentException("--execution-json deve essere un oggetto JSON { ... }")
        n.deepCopy().asInstanceOf[ObjectNode]
      } else om.createObjectNode()

    if (hasVm)
      obj.put("elasticBrowserVmCount", elasticBrowserVmCount.trim.toInt)
    if (hasCred)
      obj.put("elasticBrowserCloudCredentialId", elasticBrowserCloudCredentialId.trim.toInt)
    if (hasInfra)
      obj.put("elasticBrowserInfrastructureProvider", elasticBrowserInfraProvider.trim)
    if (hasEphem)
      obj.put(
        "useEphemeralElasticBrowserVms",
        java.lang.Boolean.parseBoolean(useEphemeralElasticBrowserVms.trim)
      )

    if (!obj.has("executionMode") || obj.get("executionMode").isNull
        || !obj.get("executionMode").isTextual
        || obj.get("executionMode").asText("").trim.isEmpty) {
      obj.put("executionMode", "SCHEDULED")
    }
    scala.Some(om.writeValueAsString(obj))
  }

  override def startRun(): Unit = {
    this.init()
    val enabled = java.lang.Boolean.parseBoolean(enabledStr)
    val sch = new ProjectScheduleRequest()
    sch.setCronSchedule(cron)
    sch.setEnabled(java.lang.Boolean.valueOf(enabled))
    sch.setTimezone(timezone)
    if (jobId != null && jobId.nonEmpty) sch.setJobId(jobId)
    mergedExecutionRequestJson().foreach(sch.setExecutionRequestJson)
    val path =
      "/webrobot/api/projects/id/" + apiClient().escapeString(projectId) + "/schedule"
    val node = OpenApiHttp.putJson(apiClient(), path, sch)
    JsonCliUtil.printJson(node)
  }
}

@Command(
  name = "project",
  mixinStandardHelpOptions = true,
  sortOptions = false,
  description = Array("Progetti (REST OpenAPI /webrobot/api/projects/...)."),
  footer = Array(),
  subcommands = Array(
    classOf[RunAddProjectCommand],
    classOf[RunGetProjectCommand],
    classOf[RunUpdateProjectCommand],
    classOf[RunListProjectCommand],
    classOf[RunDeleteProjectCommand],
    classOf[RunGetProjectScheduleCommand],
    classOf[RunSetProjectScheduleCommand]
  )
)
class RunProjectCommand extends Runnable {

  def run(): Unit = {
    System.err.println(
      "Uso: webrobot project <sottocomando>. Sottocomandi: add | get | update | list | delete | schedule-get | schedule-set"
    )
    System.err.println("Esempio: webrobot project list")
  }
}
