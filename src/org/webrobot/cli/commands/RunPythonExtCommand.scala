package org.webrobot.cli.commands

import com.fasterxml.jackson.databind.node.JsonNodeFactory
import org.webrobot.cli.openapi.{JsonCliUtil, OpenApiHttp}
import picocli.CommandLine.{Command, Option}

// ── Python Extensions ─────────────────────────────────────────────────────────

@Command(name = "info", sortOptions = false,
  description = Array("Info servizio python-extensions (GET /webrobot/api/python-extensions/info)."))
class RunPythonExtInfoCommand extends BaseSubCommand {

  override def startRun(): Unit = {
    this.init()
    JsonCliUtil.printJson(OpenApiHttp.getJson(apiClient(), "/webrobot/api/python-extensions/info"))
  }
}

@Command(name = "supported-types", sortOptions = false,
  description = Array("Tipi supportati (GET /webrobot/api/python-extensions/supported-types)."))
class RunPythonExtSupportedTypesCommand extends BaseSubCommand {

  override def startRun(): Unit = {
    this.init()
    JsonCliUtil.printJson(OpenApiHttp.getJson(apiClient(), "/webrobot/api/python-extensions/supported-types"))
  }
}

@Command(name = "list", sortOptions = false,
  description = Array("Estensioni dell'agent (GET /webrobot/api/python-extensions/agents/{agentId}/python-extensions)."))
class RunListPythonExtCommand extends BaseSubCommand {

  @Option(names = Array("-a", "--agentId"), description = Array("agent id"), required = true)
  private var agentId: String = ""

  override def startRun(): Unit = {
    this.init()
    val path = "/webrobot/api/python-extensions/agents/" + apiClient().escapeString(agentId) + "/python-extensions"
    val node = OpenApiHttp.getJson(apiClient(), path)
    if (node != null && node.isArray)
      JsonCliUtil.renderArrayGrid(node, "id", "name", "extensionType", "agentId", "createdAt")
    else JsonCliUtil.printJson(node)
  }
}

@Command(name = "agent-extensions", sortOptions = false,
  description = Array("Tutte le estensioni installate per agent (GET /webrobot/api/python-extensions/agents/{agentId}/extensions)."))
class RunAgentExtensionsCommand extends BaseSubCommand {

  @Option(names = Array("-a", "--agentId"), description = Array("agent id"), required = true)
  private var agentId: String = ""

  override def startRun(): Unit = {
    this.init()
    val path = "/webrobot/api/python-extensions/agents/" + apiClient().escapeString(agentId) + "/extensions"
    val node = OpenApiHttp.getJson(apiClient(), path)
    JsonCliUtil.printJson(node)
  }
}

@Command(name = "add", sortOptions = false,
  description = Array("Crea estensione Python (POST /webrobot/api/python-extensions/agents/{agentId}/python-extensions). Usa --json per body completo."))
class RunAddPythonExtCommand extends BaseSubCommand {

  @Option(names = Array("-a", "--agentId"), description = Array("agent id"), required = true)
  private var agentId: String = ""

  @Option(names = Array("-n", "--name"), description = Array("nome"), required = true)
  private var name: String = ""

  @Option(names = Array("--extension-type"), description = Array("extensionType"))
  private var extensionType: String = ""

  @Option(names = Array("--json"), description = Array("body JSON completo"))
  private var jsonBody: String = ""

  override def startRun(): Unit = {
    this.init()
    val om = apiClient().getObjectMapper
    val body = if (jsonBody != null && jsonBody.trim.nonEmpty) om.readTree(jsonBody.trim)
    else {
      val o = om.createObjectNode()
      o.put("name", name)
      o.put("agentId", agentId)
      if (extensionType != null && extensionType.nonEmpty) o.put("extensionType", extensionType)
      o
    }
    val path = "/webrobot/api/python-extensions/agents/" + apiClient().escapeString(agentId) + "/python-extensions"
    val node = OpenApiHttp.postJson(apiClient(), path, body)
    JsonCliUtil.printJson(node)
  }
}

@Command(name = "update", sortOptions = false,
  description = Array("Aggiorna estensione Python (PUT /webrobot/api/python-extensions/python-extensions/{extensionId})."))
class RunUpdatePythonExtCommand extends BaseSubCommand {

  @Option(names = Array("-i", "--extensionId"), description = Array("extension id"), required = true)
  private var extensionId: String = ""

  @Option(names = Array("--json"), description = Array("body JSON completo"), required = true)
  private var jsonBody: String = ""

  override def startRun(): Unit = {
    this.init()
    val om = apiClient().getObjectMapper
    val path = "/webrobot/api/python-extensions/python-extensions/" + apiClient().escapeString(extensionId)
    val node = OpenApiHttp.putJson(apiClient(), path, om.readTree(jsonBody.trim))
    JsonCliUtil.printJson(node)
  }
}

@Command(name = "delete", sortOptions = false,
  description = Array("Elimina estensione Python (DELETE /webrobot/api/python-extensions/python-extensions/{extensionId})."))
class RunDeletePythonExtCommand extends BaseSubCommand {

  @Option(names = Array("-i", "--extensionId"), description = Array("extension id"), required = true)
  private var extensionId: String = ""

  override def startRun(): Unit = {
    this.init()
    OpenApiHttp.deleteJson(apiClient(),
      "/webrobot/api/python-extensions/python-extensions/" + apiClient().escapeString(extensionId))
  }
}

@Command(name = "register", sortOptions = false,
  description = Array("Registra estensione Python (POST /webrobot/api/python-extensions/python-extensions/register)."))
class RunRegisterPythonExtCommand extends BaseSubCommand {

  @Option(names = Array("--json"), description = Array("body JSON"), required = true)
  private var jsonBody: String = ""

  override def startRun(): Unit = {
    this.init()
    val node = OpenApiHttp.postJson(apiClient(),
      "/webrobot/api/python-extensions/python-extensions/register",
      apiClient().getObjectMapper.readTree(jsonBody.trim))
    JsonCliUtil.printJson(node)
  }
}

@Command(name = "validate", sortOptions = false,
  description = Array("Valida estensione Python (POST /webrobot/api/python-extensions/validate)."))
class RunValidatePythonExtCommand extends BaseSubCommand {

  @Option(names = Array("--json"), description = Array("body JSON"), required = true)
  private var jsonBody: String = ""

  override def startRun(): Unit = {
    this.init()
    val node = OpenApiHttp.postJson(apiClient(), "/webrobot/api/python-extensions/validate",
      apiClient().getObjectMapper.readTree(jsonBody.trim))
    JsonCliUtil.printJson(node)
  }
}

@Command(name = "process-yaml", sortOptions = false,
  description = Array("Elabora YAML Python extension (POST /webrobot/api/python-extensions/process-yaml)."))
class RunProcessYamlPythonExtCommand extends BaseSubCommand {

  @Option(names = Array("--json"), description = Array("body JSON (o YAML convertito in JSON)"), required = true)
  private var jsonBody: String = ""

  override def startRun(): Unit = {
    this.init()
    val node = OpenApiHttp.postJson(apiClient(), "/webrobot/api/python-extensions/process-yaml",
      apiClient().getObjectMapper.readTree(jsonBody.trim))
    JsonCliUtil.printJson(node)
  }
}

@Command(name = "generate-pyspark", sortOptions = false,
  description = Array("Genera PySpark code (POST /webrobot/api/python-extensions/python-extensions/{extensionId}/generate-pyspark)."))
class RunGeneratePysparkCommand extends BaseSubCommand {

  @Option(names = Array("-i", "--extensionId"), description = Array("extension id"), required = true)
  private var extensionId: String = ""

  @Option(names = Array("--json"), description = Array("body JSON opzionale"))
  private var jsonBody: String = ""

  override def startRun(): Unit = {
    this.init()
    val path = "/webrobot/api/python-extensions/python-extensions/" +
      apiClient().escapeString(extensionId) + "/generate-pyspark"
    val om = apiClient().getObjectMapper
    val body = if (jsonBody != null && jsonBody.trim.nonEmpty) om.readTree(jsonBody.trim)
               else JsonNodeFactory.instance.objectNode()
    val node = OpenApiHttp.postJson(apiClient(), path, body)
    JsonCliUtil.printJson(node)
  }
}

// ── Top-level group ───────────────────────────────────────────────────────────

@Command(
  name = "python-ext",
  mixinStandardHelpOptions = true,
  sortOptions = false,
  description = Array("Python Extensions (REST /webrobot/api/python-extensions/...)."),
  subcommands = Array(
    classOf[RunPythonExtInfoCommand],
    classOf[RunPythonExtSupportedTypesCommand],
    classOf[RunListPythonExtCommand],
    classOf[RunAgentExtensionsCommand],
    classOf[RunAddPythonExtCommand],
    classOf[RunUpdatePythonExtCommand],
    classOf[RunDeletePythonExtCommand],
    classOf[RunRegisterPythonExtCommand],
    classOf[RunValidatePythonExtCommand],
    classOf[RunProcessYamlPythonExtCommand],
    classOf[RunGeneratePysparkCommand]
  )
)
class RunPythonExtCommand extends Runnable {

  def run(): Unit = {
    System.err.println("Uso: webrobot python-ext <sottocomando>.")
    System.err.println(
      "Sottocomandi: info | supported-types | list | agent-extensions | add | update | delete | register | validate | process-yaml | generate-pyspark"
    )
  }
}
