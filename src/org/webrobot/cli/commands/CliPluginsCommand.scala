package org.webrobot.cli.commands

import picocli.CommandLine
import picocli.CommandLine.Command

@Command(
  name = "plugins",
  description = Array("Manage CLI plugin JARs installed in ~/.webrobot/plugins/."),
  subcommands = Array(
    classOf[CliPluginsList],
    classOf[CliPluginsInstall],
    classOf[CliPluginsRemove],
    classOf[CliPluginsInfo]
  )
)
class CliPluginsCommand extends Runnable {
  override def run(): Unit = CommandLine.usage(this, System.out)
}
