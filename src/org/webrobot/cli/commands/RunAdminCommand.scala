package org.webrobot.cli.commands

import com.fasterxml.jackson.databind.node.JsonNodeFactory
import org.webrobot.cli.openapi.{JsonCliUtil, OpenApiHttp}
import picocli.CommandLine.{Command, Option}

// ── ETL Library Versions ──────────────────────────────────────────────────────

@Command(name = "etl-versions-list", sortOptions = false,
  description = Array("ETL library versions (GET /webrobot/api/admin/etl-library-versions)."))
class RunListEtlVersionsCommand extends BaseSubCommand {

  @Option(names = Array("--build-type"), description = Array("filtro buildType (es. SPARK, PYTHON)"))
  private var buildType: String = ""

  @Option(names = Array("--active-only"), description = Array("solo versioni attive (true|false)"))
  private var activeOnly: String = ""

  override def startRun(): Unit = {
    this.init()
    val tuples = Seq(
      scala.Option(buildType).filter(_.nonEmpty).map("buildType" -> _.asInstanceOf[AnyRef]),
      scala.Option(activeOnly).filter(_.nonEmpty).map("activeOnly" -> _.asInstanceOf[AnyRef])
    ).flatten
    val node =
      if (tuples.isEmpty) OpenApiHttp.getJson(apiClient(), "/webrobot/api/admin/etl-library-versions")
      else OpenApiHttp.getJson(apiClient(), "/webrobot/api/admin/etl-library-versions",
        OpenApiHttp.pairs(apiClient(), tuples: _*))
    if (node != null && node.isArray)
      JsonCliUtil.renderArrayGrid(node, "id", "version", "buildType", "buildNumber", "active", "createdAt")
    else JsonCliUtil.printJson(node)
  }
}

@Command(name = "etl-versions-get", sortOptions = false,
  description = Array("ETL library version per id (GET /webrobot/api/admin/etl-library-versions/id/{id})."))
class RunGetEtlVersionCommand extends BaseSubCommand {

  @Option(names = Array("-i", "--id"), description = Array("version id"), required = true)
  private var id: String = ""

  override def startRun(): Unit = {
    this.init()
    val node = OpenApiHttp.getJson(apiClient(),
      "/webrobot/api/admin/etl-library-versions/id/" + apiClient().escapeString(id))
    JsonCliUtil.printJson(node)
  }
}

@Command(name = "etl-versions-add", sortOptions = false,
  description = Array("Crea/aggiorna ETL library version (POST /webrobot/api/admin/etl-library-versions). Usa --json per body completo."))
class RunAddEtlVersionCommand extends BaseSubCommand {

  @Option(names = Array("--build-type"), description = Array("buildType (SPARK, PYTHON, ...)"), required = true)
  private var buildType: String = ""

  @Option(names = Array("--build-number"), description = Array("buildNumber"), required = true)
  private var buildNumber: String = ""

  @Option(names = Array("--version"), description = Array("version string"))
  private var version: String = ""

  @Option(names = Array("--active"), description = Array("attiva (true|false, default true)"))
  private var active: String = "true"

  @Option(names = Array("--json"), description = Array("body JSON completo"))
  private var jsonBody: String = ""

  override def startRun(): Unit = {
    this.init()
    val om = apiClient().getObjectMapper
    val body = if (jsonBody != null && jsonBody.trim.nonEmpty) om.readTree(jsonBody.trim)
    else {
      val o = om.createObjectNode()
      o.put("buildType", buildType)
      o.put("buildNumber", buildNumber.toInt)
      if (version != null && version.nonEmpty) o.put("version", version)
      o.put("active", java.lang.Boolean.parseBoolean(active))
      o
    }
    val node = OpenApiHttp.postJson(apiClient(), "/webrobot/api/admin/etl-library-versions", body)
    JsonCliUtil.printJson(node)
  }
}

@Command(name = "etl-versions-delete", sortOptions = false,
  description = Array("Elimina ETL library version (DELETE /webrobot/api/admin/etl-library-versions/id/{id})."))
class RunDeleteEtlVersionCommand extends BaseSubCommand {

  @Option(names = Array("-i", "--id"), description = Array("version id"), required = true)
  private var id: String = ""

  override def startRun(): Unit = {
    this.init()
    OpenApiHttp.deleteJson(apiClient(),
      "/webrobot/api/admin/etl-library-versions/id/" + apiClient().escapeString(id))
  }
}

// ── Plugins ───────────────────────────────────────────────────────────────────

@Command(name = "plugins-list", sortOptions = false,
  description = Array("Elenco plugin (GET /webrobot/api/admin/plugins). Filtro --build-type."))
class RunListPluginsCommand extends BaseSubCommand {

  @Option(names = Array("--build-type"), description = Array("filtro buildType"))
  private var buildType: String = ""

  override def startRun(): Unit = {
    this.init()
    val node = if (buildType != null && buildType.nonEmpty)
      OpenApiHttp.getJson(apiClient(), "/webrobot/api/admin/plugins",
        OpenApiHttp.pairs(apiClient(), "buildType" -> buildType.asInstanceOf[AnyRef]))
    else OpenApiHttp.getJson(apiClient(), "/webrobot/api/admin/plugins")
    if (node != null && node.isArray)
      JsonCliUtil.renderArrayGrid(node, "id", "name", "version", "buildType", "enabled")
    else JsonCliUtil.printJson(node)
  }
}

@Command(name = "plugins-enable", sortOptions = false,
  description = Array("Abilita plugin (POST /webrobot/api/admin/plugins/{pluginId}/enable)."))
class RunEnablePluginCommand extends BaseSubCommand {

  @Option(names = Array("-i", "--pluginId"), description = Array("plugin id"), required = true)
  private var pluginId: String = ""

  override def startRun(): Unit = {
    this.init()
    val path = "/webrobot/api/admin/plugins/" + apiClient().escapeString(pluginId) + "/enable"
    val node = OpenApiHttp.postJson(apiClient(), path, JsonNodeFactory.instance.objectNode())
    JsonCliUtil.printJson(node)
  }
}

@Command(name = "plugins-disable", sortOptions = false,
  description = Array("Disabilita plugin (POST /webrobot/api/admin/plugins/{pluginId}/disable)."))
class RunDisablePluginCommand extends BaseSubCommand {

  @Option(names = Array("-i", "--pluginId"), description = Array("plugin id"), required = true)
  private var pluginId: String = ""

  override def startRun(): Unit = {
    this.init()
    val path = "/webrobot/api/admin/plugins/" + apiClient().escapeString(pluginId) + "/disable"
    val node = OpenApiHttp.postJson(apiClient(), path, JsonNodeFactory.instance.objectNode())
    JsonCliUtil.printJson(node)
  }
}

// ── Plugin Installations ──────────────────────────────────────────────────────

@Command(name = "plugin-installations-list", sortOptions = false,
  description = Array("Elenco plugin installations (GET /webrobot/api/admin/plugin-installations)."))
class RunListPluginInstallationsCommand extends BaseSubCommand {

  @Option(names = Array("--org-id"), description = Array("filtro organizationId"))
  private var organizationId: String = ""

  @Option(names = Array("--enabled-only"), description = Array("solo abilitati (true|false)"))
  private var enabledOnly: String = ""

  override def startRun(): Unit = {
    this.init()
    val tuples = Seq(
      scala.Option(organizationId).filter(_.nonEmpty).map("organizationId" -> _.asInstanceOf[AnyRef]),
      scala.Option(enabledOnly).filter(_.nonEmpty).map("enabledOnly" -> _.asInstanceOf[AnyRef])
    ).flatten
    val node =
      if (tuples.isEmpty) OpenApiHttp.getJson(apiClient(), "/webrobot/api/admin/plugin-installations")
      else OpenApiHttp.getJson(apiClient(), "/webrobot/api/admin/plugin-installations",
        OpenApiHttp.pairs(apiClient(), tuples: _*))
    if (node != null && node.isArray)
      JsonCliUtil.renderArrayGrid(node, "id", "pluginId", "organizationId", "enabled", "createdAt")
    else JsonCliUtil.printJson(node)
  }
}

@Command(name = "plugin-installations-get", sortOptions = false,
  description = Array("Plugin installation per id (GET /webrobot/api/admin/plugin-installations/{id})."))
class RunGetPluginInstallationCommand extends BaseSubCommand {

  @Option(names = Array("-i", "--id"), description = Array("installation id"), required = true)
  private var id: String = ""

  override def startRun(): Unit = {
    this.init()
    val node = OpenApiHttp.getJson(apiClient(),
      "/webrobot/api/admin/plugin-installations/" + apiClient().escapeString(id))
    JsonCliUtil.printJson(node)
  }
}

@Command(name = "plugin-installations-enable", sortOptions = false,
  description = Array("Abilita plugin installation (POST /webrobot/api/admin/plugin-installations/{id}/enable)."))
class RunEnablePluginInstallationCommand extends BaseSubCommand {

  @Option(names = Array("-i", "--id"), description = Array("installation id"), required = true)
  private var id: String = ""

  override def startRun(): Unit = {
    this.init()
    val path = "/webrobot/api/admin/plugin-installations/" + apiClient().escapeString(id) + "/enable"
    val node = OpenApiHttp.postJson(apiClient(), path, JsonNodeFactory.instance.objectNode())
    JsonCliUtil.printJson(node)
  }
}

@Command(name = "plugin-installations-disable", sortOptions = false,
  description = Array("Disabilita plugin installation (POST /webrobot/api/admin/plugin-installations/{id}/disable)."))
class RunDisablePluginInstallationCommand extends BaseSubCommand {

  @Option(names = Array("-i", "--id"), description = Array("installation id"), required = true)
  private var id: String = ""

  override def startRun(): Unit = {
    this.init()
    val path = "/webrobot/api/admin/plugin-installations/" + apiClient().escapeString(id) + "/disable"
    val node = OpenApiHttp.postJson(apiClient(), path, JsonNodeFactory.instance.objectNode())
    JsonCliUtil.printJson(node)
  }
}

@Command(name = "plugin-installations-reload", sortOptions = false,
  description = Array("Ricarica tutti i plugin (POST /webrobot/api/admin/plugin-installations/reload)."))
class RunReloadPluginsCommand extends BaseSubCommand {

  override def startRun(): Unit = {
    this.init()
    val node = OpenApiHttp.postJson(apiClient(),
      "/webrobot/api/admin/plugin-installations/reload", JsonNodeFactory.instance.objectNode())
    JsonCliUtil.printJson(node)
  }
}

// ── System Logs & Admin Tasks ─────────────────────────────────────────────────

@Command(name = "system-logs", sortOptions = false,
  description = Array("Log di sistema (GET /webrobot/api/projects/admin/system-logs)."))
class RunSystemLogsCommand extends BaseSubCommand {

  @Option(names = Array("--service"), description = Array("filtro service"))
  private var service: String = ""

  @Option(names = Array("--level"), description = Array("filtro level (INFO, WARN, ERROR)"))
  private var level: String = ""

  @Option(names = Array("--tail"), description = Array("numero righe (tail)"))
  private var tail: String = ""

  override def startRun(): Unit = {
    this.init()
    val tuples = Seq(
      scala.Option(service).filter(_.nonEmpty).map("service" -> _.asInstanceOf[AnyRef]),
      scala.Option(level).filter(_.nonEmpty).map("level" -> _.asInstanceOf[AnyRef]),
      scala.Option(tail).filter(_.nonEmpty).map("tail" -> _.asInstanceOf[AnyRef])
    ).flatten
    val node =
      if (tuples.isEmpty) OpenApiHttp.getJson(apiClient(), "/webrobot/api/projects/admin/system-logs")
      else OpenApiHttp.getJson(apiClient(), "/webrobot/api/projects/admin/system-logs",
        OpenApiHttp.pairs(apiClient(), tuples: _*))
    JsonCliUtil.printJson(node)
  }
}

@Command(name = "mark-zombies", sortOptions = false,
  description = Array("Marca task zombie (POST /webrobot/api/projects/admin/tasks/mark-zombies)."))
class RunMarkZombiesCommand extends BaseSubCommand {

  @Option(names = Array("--timeout-hours"), description = Array("ore di timeout (default server)"))
  private var timeoutHours: String = ""

  override def startRun(): Unit = {
    this.init()
    val node = if (timeoutHours != null && timeoutHours.nonEmpty)
      OpenApiHttp.postJson(apiClient(),
        "/webrobot/api/projects/admin/tasks/mark-zombies?timeoutHours=" + timeoutHours,
        JsonNodeFactory.instance.objectNode())
    else OpenApiHttp.postJson(apiClient(),
      "/webrobot/api/projects/admin/tasks/mark-zombies", JsonNodeFactory.instance.objectNode())
    JsonCliUtil.printJson(node)
  }
}

// ── Top-level group ───────────────────────────────────────────────────────────

@Command(
  name = "admin",
  mixinStandardHelpOptions = true,
  sortOptions = false,
  description = Array("Operazioni amministrative (ETL versions, plugin, system-logs). Richiedono permessi admin."),
  subcommands = Array(
    classOf[RunListEtlVersionsCommand],
    classOf[RunGetEtlVersionCommand],
    classOf[RunAddEtlVersionCommand],
    classOf[RunDeleteEtlVersionCommand],
    classOf[RunListPluginsCommand],
    classOf[RunEnablePluginCommand],
    classOf[RunDisablePluginCommand],
    classOf[RunListPluginInstallationsCommand],
    classOf[RunGetPluginInstallationCommand],
    classOf[RunEnablePluginInstallationCommand],
    classOf[RunDisablePluginInstallationCommand],
    classOf[RunReloadPluginsCommand],
    classOf[RunSystemLogsCommand],
    classOf[RunMarkZombiesCommand]
  )
)
class RunAdminCommand extends Runnable {

  def run(): Unit = {
    System.err.println("Uso: webrobot admin <sottocomando>.")
    System.err.println(
      "Sottocomandi: etl-versions-list | etl-versions-get | etl-versions-add | etl-versions-delete |"
    )
    System.err.println(
      "  plugins-list | plugins-enable | plugins-disable |"
    )
    System.err.println(
      "  plugin-installations-list | plugin-installations-get | plugin-installations-enable | plugin-installations-disable | plugin-installations-reload |"
    )
    System.err.println("  system-logs | mark-zombies")
  }
}
