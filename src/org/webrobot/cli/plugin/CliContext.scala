package org.webrobot.cli.plugin

import com.fasterxml.jackson.databind.JsonNode
import eu.webrobot.cli.sdk.{CliConfig, OrgIdentity, OutputFormatter, WebroApiClient, WebroCliContext}
import org.slf4j.{Logger, LoggerFactory}

import java.nio.file.{Files, Path, Paths}
import java.util.{Collections, Optional, Set => JSet}

/**
 * Default WebroCliContext built once per CLI invocation and handed to every plugin.
 *
 * Wires the public CLI plugin SDK contracts to the CLI's internal services:
 *  - WebroApiClient → CliApiClient → GenericClient (OpenApiSdkAdapter)
 *  - CliConfig      → reads ~/.webrobot/config.cfg (or whatever the active config is)
 *  - OrgIdentity    → resolved from API key / JWT validation (best-effort)
 *  - OutputFormatter → routes to ConsoleOutputFormatter (table/json/yaml)
 */
final class CliContext private (
  api:           WebroApiClient,
  config:        CliConfig,
  orgIdentity:   OrgIdentity,
  output:        OutputFormatter,
  logger:        Logger,
  pluginsRoot:   Path,
  cliVersion:    String
) extends WebroCliContext {

  override def api(): WebroApiClient                     = api
  override def config(): CliConfig                       = config
  override def org(): OrgIdentity                        = orgIdentity
  override def output(): OutputFormatter                 = output
  override def log(): Logger                             = logger
  override def cliVersion(): String                      = cliVersion

  override def pluginDataDir(pluginId: String): Path = {
    val dir = pluginsRoot.resolve("data").resolve(pluginId)
    if (!Files.isDirectory(dir)) Files.createDirectories(dir)
    dir
  }
}

object CliContext {
  private val DefaultPluginsRoot = Paths.get(System.getProperty("user.home"), ".webrobot", "plugins")
  private val DefaultLogger      = LoggerFactory.getLogger("webrobot-cli-plugin")

  /** Compatibility version this CLI exposes via WebroCliContext.cliVersion. */
  val PluginApiVersion = "0.2.0"

  /**
   * Build a context using the platform's resolved API client and config.
   * The api parameter is a fully-configured CliApiClient (auth + endpoint applied).
   */
  def build(
    api:        WebroApiClient,
    config:     CliConfig,
    orgIdentity: OrgIdentity,
    output:     OutputFormatter,
    logger:     Logger = DefaultLogger,
    pluginsRoot: Path  = DefaultPluginsRoot
  ): WebroCliContext =
    new CliContext(api, config, orgIdentity, output, logger, pluginsRoot, PluginApiVersion)
}

// ── Default lightweight implementations of OrgIdentity / CliConfig ─────────────
// These can be replaced by smarter ones once the CLI knows how to derive
// org_id from the JWT or call /auth/me — the contract is what matters here.

final case class StaticOrgIdentity(orgId: String, uid: String, roleSet: JSet[String], resolved: Boolean)
    extends OrgIdentity {
  override def organizationId(): String = orgId
  override def userId(): String         = uid
  override def roles(): JSet[String]    = roleSet
  override def isResolved(): Boolean    = resolved
}

object StaticOrgIdentity {
  def unresolved: OrgIdentity =
    StaticOrgIdentity("", "", Collections.emptySet[String](), resolved = false)
}

final class CliConfigFromMap(values: java.util.Map[String, String], file: Path) extends CliConfig {
  override def apiEndpoint(): String =
    Option(values.get("api_endpoint")).getOrElse("https://api.webrobot.eu")
  override def get(key: String): Optional[String] = Optional.ofNullable(values.get(key))
  override def configFile(): Path = file
}
