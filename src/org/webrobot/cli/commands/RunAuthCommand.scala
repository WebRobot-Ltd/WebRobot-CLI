package org.webrobot.cli.commands

import org.webrobot.cli.openapi.{JsonCliUtil, OpenApiHttp}
import picocli.CommandLine.{Command, Option}

// ── /webrobot/api/auth/me ────────────────────────────────────────────────────

@Command(
  name = "me",
  sortOptions = false,
  description = Array("Utente corrente (GET /webrobot/api/auth/me).")
)
class RunAuthMeCommand extends BaseSubCommand {

  override def startRun(): Unit = {
    this.init()
    val node = OpenApiHttp.getJson(apiClient(), "/webrobot/api/auth/me")
    JsonCliUtil.printJson(node)
  }
}

// ── /webrobot/api/auth/api-keys ──────────────────────────────────────────────

@Command(
  name = "api-keys-list",
  sortOptions = false,
  description = Array("Elenco API keys (GET /webrobot/api/auth/api-keys).")
)
class RunListApiKeysCommand extends BaseSubCommand {

  @Option(names = Array("--organization"), description = Array("filtro organizationId"))
  private var organization: String = ""

  @Option(names = Array("--organization-code"), description = Array("filtro organization_code"))
  private var organizationCode: String = ""

  override def startRun(): Unit = {
    this.init()
    val tuples = Seq(
      scala.Option(organization).filter(_.nonEmpty).map("organization" -> _.asInstanceOf[AnyRef]),
      scala.Option(organizationCode).filter(_.nonEmpty).map("organization_code" -> _.asInstanceOf[AnyRef])
    ).flatten
    val node =
      if (tuples.isEmpty) OpenApiHttp.getJson(apiClient(), "/webrobot/api/auth/api-keys")
      else OpenApiHttp.getJson(apiClient(), "/webrobot/api/auth/api-keys", OpenApiHttp.pairs(apiClient(), tuples: _*))
    if (node != null && node.isArray)
      JsonCliUtil.renderArrayGrid(node, "id", "name", "key", "organizationId", "createdAt")
    else JsonCliUtil.printJson(node)
  }
}

@Command(
  name = "api-keys-add",
  sortOptions = false,
  description = Array("Crea API key (POST /webrobot/api/auth/api-keys).")
)
class RunAddApiKeyCommand extends BaseSubCommand {

  @Option(names = Array("-n", "--name"), description = Array("nome chiave"), required = true)
  private var name: String = ""

  @Option(names = Array("--org-id"), description = Array("organizationId"))
  private var organizationId: String = ""

  override def startRun(): Unit = {
    this.init()
    val om = apiClient().getObjectMapper
    val body = om.createObjectNode()
    body.put("name", name)
    if (organizationId != null && organizationId.nonEmpty) body.put("organizationId", organizationId)
    val node = OpenApiHttp.postJson(apiClient(), "/webrobot/api/auth/api-keys", body)
    JsonCliUtil.printJson(node)
  }
}

@Command(
  name = "api-keys-delete",
  sortOptions = false,
  description = Array("Elimina API key (DELETE /webrobot/api/auth/api-keys/{key_id}).")
)
class RunDeleteApiKeyCommand extends BaseSubCommand {

  @Option(names = Array("-i", "--id"), description = Array("key_id"), required = true)
  private var keyId: String = ""

  override def startRun(): Unit = {
    this.init()
    val path = "/webrobot/api/auth/api-keys/" + apiClient().escapeString(keyId)
    OpenApiHttp.deleteJson(apiClient(), path)
  }
}

// ── /webrobot/api/auth/organizations ─────────────────────────────────────────

@Command(
  name = "org-get",
  sortOptions = false,
  description = Array("Dettaglio organizzazione (GET /webrobot/api/auth/organizations/{id}).")
)
class RunGetOrganizationCommand extends BaseSubCommand {

  @Option(names = Array("-i", "--id"), description = Array("organization id"), required = true)
  private var orgId: String = ""

  override def startRun(): Unit = {
    this.init()
    val path = "/webrobot/api/auth/organizations/" + apiClient().escapeString(orgId)
    val node = OpenApiHttp.getJson(apiClient(), path)
    JsonCliUtil.printJson(node)
  }
}

@Command(
  name = "org-users",
  sortOptions = false,
  description = Array("Utenti dell'organizzazione (GET /webrobot/api/auth/organizations/{id}/users).")
)
class RunListOrgUsersCommand extends BaseSubCommand {

  @Option(names = Array("-i", "--id"), description = Array("organization id"), required = true)
  private var orgId: String = ""

  override def startRun(): Unit = {
    this.init()
    val path = "/webrobot/api/auth/organizations/" + apiClient().escapeString(orgId) + "/users"
    val node = OpenApiHttp.getJson(apiClient(), path)
    if (node != null && node.isArray)
      JsonCliUtil.renderArrayGrid(node, "id", "email", "name", "role", "createdAt")
    else JsonCliUtil.printJson(node)
  }
}

@Command(
  name = "org-create",
  sortOptions = false,
  description = Array("Crea organizzazione (POST /webrobot/api/auth/organizations).")
)
class RunCreateOrganizationCommand extends BaseSubCommand {

  @Option(names = Array("-n", "--name"), description = Array("nome organizzazione"), required = true)
  private var name: String = ""

  @Option(names = Array("--json"), description = Array("body JSON completo (sovrascrive --name)"))
  private var jsonBody: String = ""

  override def startRun(): Unit = {
    this.init()
    val om = apiClient().getObjectMapper
    val body = if (jsonBody != null && jsonBody.trim.nonEmpty) om.readTree(jsonBody.trim)
               else { val o = om.createObjectNode(); o.put("name", name); o }
    val node = OpenApiHttp.postJson(apiClient(), "/webrobot/api/auth/organizations", body)
    JsonCliUtil.printJson(node)
  }
}

@Command(
  name = "user-invites",
  sortOptions = false,
  description = Array("Inviti utente in sospeso (GET /webrobot/api/auth/user-invites).")
)
class RunListUserInvitesCommand extends BaseSubCommand {

  override def startRun(): Unit = {
    this.init()
    val node = OpenApiHttp.getJson(apiClient(), "/webrobot/api/auth/user-invites")
    if (node != null && node.isArray)
      JsonCliUtil.renderArrayGrid(node, "id", "email", "organizationId", "createdAt")
    else JsonCliUtil.printJson(node)
  }
}

// ── Top-level group ───────────────────────────────────────────────────────────

@Command(
  name = "auth",
  mixinStandardHelpOptions = true,
  sortOptions = false,
  description = Array("Auth & utenti (REST /webrobot/api/auth/...). API keys, organizzazioni, utenti correnti."),
  subcommands = Array(
    classOf[RunAuthMeCommand],
    classOf[RunListApiKeysCommand],
    classOf[RunAddApiKeyCommand],
    classOf[RunDeleteApiKeyCommand],
    classOf[RunGetOrganizationCommand],
    classOf[RunListOrgUsersCommand],
    classOf[RunCreateOrganizationCommand],
    classOf[RunListUserInvitesCommand]
  )
)
class RunAuthCommand extends Runnable {

  def run(): Unit = {
    System.err.println(
      "Uso: webrobot auth <sottocomando>."
    )
    System.err.println(
      "Sottocomandi: me | api-keys-list | api-keys-add | api-keys-delete | org-get | org-users | org-create | user-invites"
    )
  }
}
