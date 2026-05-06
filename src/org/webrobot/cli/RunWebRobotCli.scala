package org.webrobot.cli

import java.io.File
import java.nio.file.Paths
import java.util.{HashMap => JHashMap}

import WebRobot.Cli.Sdk.openapi.OpenApiSdkAdapter
import com.typesafe.config.{Config, ConfigFactory}
import org.webrobot.cli.plugin.{CliApiClient, CliConfigFromMap, CliContext, ConsoleOutputFormatter, StaticOrgIdentity}
import picocli.CommandLine

/**
 * Entry point: carica `config.cfg` (directory corrente) o fallback su classpath `config.cfg`,
 * poi esegue Picocli (subcommand `project`, `agent`, `dataset`, …). SDK: webrobot.eu:org.webrobot.sdk da GitHub Packages.
 */
object RunWebRobotCli extends App {

  /** Nodo `webrobot.api.gateway.credentials` usato da [[org.webrobot.cli.commands.BaseSubCommand]]. */
  var config: Config = _
  private var resolvedConfigFile: java.io.File = _

  private def loadCredentialsConfig(): Config = {
    val home   = new File(System.getProperty("user.home"), ".webrobot/config.cfg")
    val local  = new File("config.cfg")
    val (root, file) =
      if (home.exists())                                    (ConfigFactory.parseFile(home),               home)
      else if (local.exists())                              (ConfigFactory.parseFile(local),              local)
      else if (getClass.getResource("/config.cfg") != null) (ConfigFactory.parseResources("config.cfg"),  null)
      else (ConfigFactory.parseString(
        """webrobot.api.gateway { credentials { apikey = "" api_endpoint = "https://api.webrobot.eu" } }"""), null)
    resolvedConfigFile = file
    ConfigFactory.load(root).getConfig("webrobot.api.gateway").getConfig("credentials")
  }

  private def buildPluginContext(creds: Config): eu.webrobot.cli.sdk.WebroCliContext = {
    val endpoint = if (creds.hasPath("api_endpoint")) creds.getString("api_endpoint") else "https://api.webrobot.eu"
    val apiKey   = if (creds.hasPath("apikey"))       creds.getString("apikey")       else ""
    val jwt      = if (creds.hasPath("jwt"))          creds.getString("jwt")          else ""

    val adapter = new OpenApiSdkAdapter(endpoint)
    if (jwt != null && !jwt.isEmpty)              adapter.withApiKey("Authorization", "Bearer " + jwt)
    else if (apiKey != null && !apiKey.isEmpty)   adapter.withApiKey("X-API-Key", apiKey)

    val apiClient = new CliApiClient(adapter.generic())
    val configMap = new JHashMap[String, String]()
    configMap.put("api_endpoint", endpoint)
    if (apiKey != null && !apiKey.isEmpty) configMap.put("apikey", apiKey)
    if (jwt != null && !jwt.isEmpty)       configMap.put("jwt", jwt)

    val cliConfig = new CliConfigFromMap(
      configMap,
      if (resolvedConfigFile != null) resolvedConfigFile.toPath else Paths.get(""))
    val output    = new ConsoleOutputFormatter(System.out, "table")
    val identity  = StaticOrgIdentity.unresolved // TODO: derive from /auth/me when JWT is set

    CliContext.build(apiClient, cliConfig, identity, output)
  }

  config = loadCredentialsConfig()
  val cmd  = new CommandLine(new WebRobotCliCommand())
  val pctx = buildPluginContext(config)
  CliPluginLoader.loadAll(cmd, pctx)
  val exitCode = cmd.execute(args: _*)
  sys.exit(exitCode)
}
