package org.webrobot.cli.commands

import com.fasterxml.jackson.databind.node.JsonNodeFactory
import org.webrobot.cli.openapi.{JsonCliUtil, OpenApiHttp}
import picocli.CommandLine.{Command, Option}

// ── Scheduler (CronJobs) ──────────────────────────────────────────────────────

@Command(name = "cronjobs-list", sortOptions = false,
  description = Array("Elenco CronJob (GET /webrobot/cloud/scheduler/cronjobs). --namespace opzionale."))
class RunListCronJobsCommand extends BaseSubCommand {

  @Option(names = Array("--namespace"), description = Array("namespace Kubernetes (opzionale)"))
  private var namespace: String = ""

  override def startRun(): Unit = {
    this.init()
    val node =
      if (namespace != null && namespace.nonEmpty)
        OpenApiHttp.getJson(apiClient(), "/webrobot/cloud/scheduler/cronjobs",
          OpenApiHttp.pairs(apiClient(), "namespace" -> namespace.asInstanceOf[AnyRef]))
      else OpenApiHttp.getJson(apiClient(), "/webrobot/cloud/scheduler/cronjobs")
    if (node != null && node.isArray)
      JsonCliUtil.renderArrayGrid(node, "name", "namespace", "schedule", "active", "lastScheduleTime")
    else JsonCliUtil.printJson(node)
  }
}

@Command(name = "cronjobs-get", sortOptions = false,
  description = Array("Dettaglio CronJob (GET /webrobot/cloud/scheduler/cronjobs/{name})."))
class RunGetCronJobCommand extends BaseSubCommand {

  @Option(names = Array("-n", "--name"), description = Array("nome CronJob"), required = true)
  private var name: String = ""

  @Option(names = Array("--namespace"), description = Array("namespace Kubernetes"))
  private var namespace: String = ""

  override def startRun(): Unit = {
    this.init()
    val path = "/webrobot/cloud/scheduler/cronjobs/" + apiClient().escapeString(name)
    val node =
      if (namespace != null && namespace.nonEmpty)
        OpenApiHttp.getJson(apiClient(), path,
          OpenApiHttp.pairs(apiClient(), "namespace" -> namespace.asInstanceOf[AnyRef]))
      else OpenApiHttp.getJson(apiClient(), path)
    JsonCliUtil.printJson(node)
  }
}

@Command(name = "cronjobs-add", sortOptions = false,
  description = Array("Crea CronJob (POST /webrobot/cloud/scheduler/cronjobs). Usa --json per il body (CronJobRequest)."))
class RunAddCronJobCommand extends BaseSubCommand {

  @Option(names = Array("--json"), description = Array("body JSON (CronJobRequest)"), required = true)
  private var jsonBody: String = ""

  @Option(names = Array("--namespace"), description = Array("namespace (query param opzionale)"))
  private var namespace: String = ""

  override def startRun(): Unit = {
    this.init()
    val path = if (namespace != null && namespace.nonEmpty)
      "/webrobot/cloud/scheduler/cronjobs?namespace=" + apiClient().escapeString(namespace)
    else "/webrobot/cloud/scheduler/cronjobs"
    val node = OpenApiHttp.postJson(apiClient(), path, apiClient().getObjectMapper.readTree(jsonBody.trim))
    JsonCliUtil.printJson(node)
  }
}

@Command(name = "cronjobs-delete", sortOptions = false,
  description = Array("Elimina CronJob (DELETE /webrobot/cloud/scheduler/cronjobs/{name})."))
class RunDeleteCronJobCommand extends BaseSubCommand {

  @Option(names = Array("-n", "--name"), description = Array("nome CronJob"), required = true)
  private var name: String = ""

  @Option(names = Array("--namespace"), description = Array("namespace Kubernetes"))
  private var namespace: String = ""

  override def startRun(): Unit = {
    this.init()
    val path = "/webrobot/cloud/scheduler/cronjobs/" + apiClient().escapeString(name)
    val node =
      if (namespace != null && namespace.nonEmpty)
        OpenApiHttp.getJson(apiClient(), path,
          OpenApiHttp.pairs(apiClient(), "namespace" -> namespace.asInstanceOf[AnyRef]))
      else OpenApiHttp.deleteJson(apiClient(), path)
    JsonCliUtil.printJson(node)
  }
}

// ── Spark ─────────────────────────────────────────────────────────────────────

@Command(name = "spark-info", sortOptions = false,
  description = Array("Info Spark (GET /webrobot/cloud/spark/info)."))
class RunSparkInfoCommand extends BaseSubCommand {

  override def startRun(): Unit = {
    this.init()
    JsonCliUtil.printJson(OpenApiHttp.getJson(apiClient(), "/webrobot/cloud/spark/info"))
  }
}

@Command(name = "spark-health", sortOptions = false,
  description = Array("Health Spark (GET /webrobot/cloud/spark/health)."))
class RunSparkHealthCommand extends BaseSubCommand {

  override def startRun(): Unit = {
    this.init()
    JsonCliUtil.printJson(OpenApiHttp.getJson(apiClient(), "/webrobot/cloud/spark/health"))
  }
}

@Command(name = "spark-capabilities", sortOptions = false,
  description = Array("Capabilities Spark (GET /webrobot/cloud/spark/capabilities)."))
class RunSparkCapabilitiesCommand extends BaseSubCommand {

  override def startRun(): Unit = {
    this.init()
    JsonCliUtil.printJson(OpenApiHttp.getJson(apiClient(), "/webrobot/cloud/spark/capabilities"))
  }
}

// ── Training Cloud ────────────────────────────────────────────────────────────

@Command(name = "training-info", sortOptions = false,
  description = Array("Info servizio training (GET /webrobot/cloud/training/info)."))
class RunCloudTrainingInfoCommand extends BaseSubCommand {

  override def startRun(): Unit = {
    this.init()
    JsonCliUtil.printJson(OpenApiHttp.getJson(apiClient(), "/webrobot/cloud/training/info"))
  }
}

@Command(name = "training-health", sortOptions = false,
  description = Array("Health servizio training (GET /webrobot/cloud/training/health)."))
class RunCloudTrainingHealthCommand extends BaseSubCommand {

  override def startRun(): Unit = {
    this.init()
    JsonCliUtil.printJson(OpenApiHttp.getJson(apiClient(), "/webrobot/cloud/training/health"))
  }
}

// ── Top-level group ───────────────────────────────────────────────────────────

@Command(
  name = "cloud",
  mixinStandardHelpOptions = true,
  sortOptions = false,
  description = Array("Cloud services: scheduler (CronJob), Spark, Training (REST /webrobot/cloud/...)."),
  subcommands = Array(
    classOf[RunListCronJobsCommand],
    classOf[RunGetCronJobCommand],
    classOf[RunAddCronJobCommand],
    classOf[RunDeleteCronJobCommand],
    classOf[RunSparkInfoCommand],
    classOf[RunSparkHealthCommand],
    classOf[RunSparkCapabilitiesCommand],
    classOf[RunCloudTrainingInfoCommand],
    classOf[RunCloudTrainingHealthCommand]
  )
)
class RunCloudCommand extends Runnable {

  def run(): Unit = {
    System.err.println("Uso: webrobot cloud <sottocomando>.")
    System.err.println(
      "Sottocomandi: cronjobs-list | cronjobs-get | cronjobs-add | cronjobs-delete | spark-info | spark-health | spark-capabilities | training-info | training-health"
    )
  }
}
