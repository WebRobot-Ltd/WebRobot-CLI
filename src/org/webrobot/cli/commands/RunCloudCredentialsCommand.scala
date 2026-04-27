package org.webrobot.cli.commands

import org.webrobot.cli.openapi.{JsonCliUtil, OpenApiHttp}
import picocli.CommandLine.{Command, Option}

@Command(
  name = "list",
  sortOptions = false,
  description = Array("Elenco cloud credentials (GET /webrobot/api/cloud-credentials). Filtri opzionali: --provider, --page, --page-size.")
)
class RunListCloudCredentialsCommand extends BaseSubCommand {

  @Option(names = Array("--provider"), description = Array("filtro provider (es. hetzner, aws, gcp, azure)"))
  private var provider: String = ""

  @Option(names = Array("--page"), description = Array("pagina (0-based)"))
  private var page: String = ""

  @Option(names = Array("--page-size"), description = Array("dimensione pagina"))
  private var pageSize: String = ""

  override def startRun(): Unit = {
    this.init()
    val tuples = Seq(
      scala.Option(provider).filter(_.nonEmpty).map("provider" -> _.asInstanceOf[AnyRef]),
      scala.Option(page).filter(_.nonEmpty).map("page" -> _.asInstanceOf[AnyRef]),
      scala.Option(pageSize).filter(_.nonEmpty).map("pageSize" -> _.asInstanceOf[AnyRef])
    ).flatten
    val node =
      if (tuples.isEmpty) OpenApiHttp.getJson(apiClient(), "/webrobot/api/cloud-credentials")
      else OpenApiHttp.getJson(apiClient(), "/webrobot/api/cloud-credentials", OpenApiHttp.pairs(apiClient(), tuples: _*))
    if (node != null && node.isArray)
      JsonCliUtil.renderArrayGrid(node, "id", "name", "description", "provider", "enabled", "createdAt")
    else JsonCliUtil.printJson(node)
  }
}

@Command(
  name = "get",
  sortOptions = false,
  description = Array("Dettaglio credential (GET /webrobot/api/cloud-credentials/id/{credentialId}).")
)
class RunGetCloudCredentialCommand extends BaseSubCommand {

  @Option(names = Array("-i", "--id"), description = Array("credential id"), required = true)
  private var credentialId: String = ""

  override def startRun(): Unit = {
    this.init()
    val path = "/webrobot/api/cloud-credentials/id/" + apiClient().escapeString(credentialId)
    val node = OpenApiHttp.getJson(apiClient(), path)
    JsonCliUtil.printJson(node)
  }
}

@Command(
  name = "by-provider",
  sortOptions = false,
  description = Array("Credential per provider (GET /webrobot/api/cloud-credentials/provider/{provider}).")
)
class RunGetCloudCredentialByProviderCommand extends BaseSubCommand {

  @Option(names = Array("--provider"), description = Array("provider (es. hetzner, aws, gcp, azure)"), required = true)
  private var provider: String = ""

  override def startRun(): Unit = {
    this.init()
    val path = "/webrobot/api/cloud-credentials/provider/" + apiClient().escapeString(provider)
    val node = OpenApiHttp.getJson(apiClient(), path)
    if (node != null && node.isArray)
      JsonCliUtil.renderArrayGrid(node, "id", "name", "description", "provider", "enabled")
    else JsonCliUtil.printJson(node)
  }
}

@Command(
  name = "add",
  sortOptions = false,
  description = Array("Crea cloud credential (POST /webrobot/api/cloud-credentials). Usa --json per body completo.")
)
class RunAddCloudCredentialCommand extends BaseSubCommand {

  @Option(names = Array("-n", "--name"), description = Array("nome"), required = true)
  private var name: String = ""

  @Option(names = Array("-d", "--description"), description = Array("descrizione"))
  private var description: String = ""

  @Option(names = Array("--provider"), description = Array("provider (hetzner, aws, gcp, azure, huggingface, ...)"), required = true)
  private var provider: String = ""

  @Option(names = Array("--api-key"), description = Array("apiKey generica"))
  private var apiKey: String = ""

  @Option(names = Array("--api-secret"), description = Array("apiSecret generica"))
  private var apiSecret: String = ""

  @Option(names = Array("--region"), description = Array("region"))
  private var region: String = ""

  @Option(names = Array("--endpoint"), description = Array("endpoint"))
  private var endpoint: String = ""

  @Option(names = Array("--access-key-id"), description = Array("accessKeyId (AWS/S3)"))
  private var accessKeyId: String = ""

  @Option(names = Array("--secret-access-key"), description = Array("secretAccessKey (AWS/S3)"))
  private var secretAccessKey: String = ""

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
      if (description != null && description.nonEmpty) o.put("description", description)
      o.put("provider", provider)
      if (apiKey != null && apiKey.nonEmpty) o.put("apiKey", apiKey)
      if (apiSecret != null && apiSecret.nonEmpty) o.put("apiSecret", apiSecret)
      if (region != null && region.nonEmpty) o.put("region", region)
      if (endpoint != null && endpoint.nonEmpty) o.put("endpoint", endpoint)
      if (accessKeyId != null && accessKeyId.nonEmpty) o.put("accessKeyId", accessKeyId)
      if (secretAccessKey != null && secretAccessKey.nonEmpty) o.put("secretAccessKey", secretAccessKey)
      o
    }
    val node = OpenApiHttp.postJson(apiClient(), "/webrobot/api/cloud-credentials", body)
    JsonCliUtil.printJson(node)
  }
}

@Command(
  name = "update",
  sortOptions = false,
  description = Array("Aggiorna cloud credential (PUT /webrobot/api/cloud-credentials/id/{credentialId}). Usa --json per body completo.")
)
class RunUpdateCloudCredentialCommand extends BaseSubCommand {

  @Option(names = Array("-i", "--id"), description = Array("credential id"), required = true)
  private var credentialId: String = ""

  @Option(names = Array("-n", "--name"), description = Array("nome"))
  private var name: String = ""

  @Option(names = Array("-d", "--description"), description = Array("descrizione"))
  private var description: String = ""

  @Option(names = Array("--provider"), description = Array("provider"))
  private var provider: String = ""

  @Option(names = Array("--api-key"), description = Array("apiKey"))
  private var apiKey: String = ""

  @Option(names = Array("--api-secret"), description = Array("apiSecret"))
  private var apiSecret: String = ""

  @Option(names = Array("--region"), description = Array("region"))
  private var region: String = ""

  @Option(names = Array("--endpoint"), description = Array("endpoint"))
  private var endpoint: String = ""

  @Option(names = Array("--json"), description = Array("body JSON completo (sovrascrive tutti gli altri flag)"))
  private var jsonBody: String = ""

  override def startRun(): Unit = {
    this.init()
    val path = "/webrobot/api/cloud-credentials/id/" + apiClient().escapeString(credentialId)
    val om = apiClient().getObjectMapper
    val body = if (jsonBody != null && jsonBody.trim.nonEmpty) {
      om.readTree(jsonBody.trim)
    } else {
      val cur = OpenApiHttp.getJson(apiClient(), path)
      val merged = if (cur != null && cur.isObject) cur.deepCopy().asInstanceOf[com.fasterxml.jackson.databind.node.ObjectNode]
                   else om.createObjectNode()
      if (name != null && name.nonEmpty) merged.put("name", name)
      if (description != null && description.nonEmpty) merged.put("description", description)
      if (provider != null && provider.nonEmpty) merged.put("provider", provider)
      if (apiKey != null && apiKey.nonEmpty) merged.put("apiKey", apiKey)
      if (apiSecret != null && apiSecret.nonEmpty) merged.put("apiSecret", apiSecret)
      if (region != null && region.nonEmpty) merged.put("region", region)
      if (endpoint != null && endpoint.nonEmpty) merged.put("endpoint", endpoint)
      merged
    }
    val node = OpenApiHttp.putJson(apiClient(), path, body)
    JsonCliUtil.printJson(node)
  }
}

@Command(
  name = "delete",
  sortOptions = false,
  description = Array("Elimina cloud credential (DELETE /webrobot/api/cloud-credentials/id/{credentialId}).")
)
class RunDeleteCloudCredentialCommand extends BaseSubCommand {

  @Option(names = Array("-i", "--id"), description = Array("credential id"), required = true)
  private var credentialId: String = ""

  override def startRun(): Unit = {
    this.init()
    val path = "/webrobot/api/cloud-credentials/id/" + apiClient().escapeString(credentialId)
    OpenApiHttp.deleteJson(apiClient(), path)
  }
}

@Command(
  name = "test",
  sortOptions = false,
  description = Array("Testa una cloud credential (POST /webrobot/api/cloud-credentials/test). Body JSON: { id: ... } o body completo.")
)
class RunTestCloudCredentialCommand extends BaseSubCommand {

  @Option(names = Array("-i", "--id"), description = Array("credential id da testare"))
  private var credentialId: String = ""

  @Option(names = Array("--json"), description = Array("body JSON completo (opzionale)"))
  private var jsonBody: String = ""

  override def startRun(): Unit = {
    this.init()
    val om = apiClient().getObjectMapper
    val body = if (jsonBody != null && jsonBody.trim.nonEmpty) {
      om.readTree(jsonBody.trim)
    } else {
      val o = om.createObjectNode()
      if (credentialId != null && credentialId.nonEmpty) o.put("id", credentialId.toInt)
      o
    }
    val node = OpenApiHttp.postJson(apiClient(), "/webrobot/api/cloud-credentials/test", body)
    JsonCliUtil.printJson(node)
  }
}

@Command(
  name = "cloud-credentials",
  mixinStandardHelpOptions = true,
  sortOptions = false,
  description = Array("Cloud credentials (REST /webrobot/api/cloud-credentials/...). Provider supportati: hetzner, aws, gcp, azure, huggingface, ..."),
  subcommands = Array(
    classOf[RunListCloudCredentialsCommand],
    classOf[RunGetCloudCredentialCommand],
    classOf[RunGetCloudCredentialByProviderCommand],
    classOf[RunAddCloudCredentialCommand],
    classOf[RunUpdateCloudCredentialCommand],
    classOf[RunDeleteCloudCredentialCommand],
    classOf[RunTestCloudCredentialCommand]
  )
)
class RunCloudCredentialsCommand extends Runnable {

  def run(): Unit = {
    System.err.println(
      "Uso: webrobot cloud-credentials <sottocomando>. Sottocomandi: list | get | by-provider | add | update | delete | test"
    )
    System.err.println("Esempio: webrobot cloud-credentials list --provider hetzner")
  }
}
