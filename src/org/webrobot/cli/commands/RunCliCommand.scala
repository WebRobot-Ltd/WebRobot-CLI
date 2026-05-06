package org.webrobot.cli.commands

import picocli.CommandLine
import picocli.CommandLine.Command

/**
 * Parent group for CLI self-management subcommands. Currently exposes:
 *   webrobot cli plugins {list, install, remove, info, update}
 *
 * Distinct from the existing `webrobot plugin` factory (which scaffolds new
 * partner plugin source trees). The `cli plugins` subtree manages the JARs
 * installed at runtime in ~/.webrobot/plugins/.
 */
@Command(
  name = "cli",
  description = Array("WebRobot CLI self-management (plugin installation, etc.)."),
  subcommands = Array(classOf[CliPluginsCommand])
)
class RunCliCommand extends Runnable {
  override def run(): Unit = CommandLine.usage(this, System.out)
}
