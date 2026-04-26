package org.webrobot.cli

import java.io.File

import com.typesafe.config.{Config, ConfigFactory}
import picocli.CommandLine

/**
 * Entry point: carica `config.cfg` (directory corrente) o fallback su classpath `config.cfg`,
 * poi esegue Picocli (subcommand `project`, `agent`, `dataset`, …). SDK: webrobot.eu:org.webrobot.sdk da GitHub Packages.
 */
object RunWebRobotCli extends App {

  /** Nodo `webrobot.api.gateway.credentials` usato da [[org.webrobot.cli.commands.BaseSubCommand]]. */
  var config: Config = _

  private def loadCredentialsConfig(): Config = {
    val local = new File("config.cfg")
    val root =
      if (local.exists()) ConfigFactory.parseFile(local)
      else if (getClass.getResource("/config.cfg") != null) ConfigFactory.parseResources("config.cfg")
      else {
        ConfigFactory.parseString(
          """webrobot.api.gateway {
            credentials {
              apikey = ""
              api_endpoint = "https://api.webrobot.eu"
            }
          }""")
      }
    ConfigFactory.load(root).getConfig("webrobot.api.gateway").getConfig("credentials")
  }

  config = loadCredentialsConfig()
  val exitCode = new CommandLine(new WebRobotCliCommand()).execute(args: _*)
  sys.exit(exitCode)
}
