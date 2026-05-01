package org.webrobot.cli

import eu.webrobot.cli.sdk.WebroCliPlugin
import picocli.CommandLine

import java.io.File
import java.net.URLClassLoader
import java.util.ServiceLoader
import scala.collection.JavaConverters._

/**
 * Discovers WebroCliPlugin implementations from JARs placed in ~/.webrobot/plugins/
 * and registers their @Command classes as subcommands of the root CommandLine.
 *
 * Plugin JARs are loaded via a URLClassLoader that delegates to the current
 * classloader, so all CLI and Picocli classes are shared — no duplicate types.
 */
object CliPluginLoader {

  private val pluginsDir = new File(System.getProperty("user.home"), ".webrobot/plugins")

  def loadAll(root: CommandLine): Unit = {
    val jars = discoverJars()
    if (jars.isEmpty) return

    val urls        = jars.map(_.toURI.toURL).toArray
    val classLoader = new URLClassLoader(urls, Thread.currentThread().getContextClassLoader())

    val plugins = ServiceLoader.load(classOf[WebroCliPlugin], classLoader).asScala.toSeq

    plugins.foreach { plugin =>
      plugin.commands().asScala.foreach { cmdClass =>
        try {
          val instance = cmdClass.getDeclaredConstructor().newInstance()
          root.addSubcommand(new CommandLine(instance))
        } catch {
          case e: Exception =>
            System.err.println(s"[webrobot] failed to load command ${cmdClass.getName} " +
              s"from plugin ${plugin.pluginId()}: ${e.getMessage}")
        }
      }
    }

    if (plugins.nonEmpty)
      System.err.println(s"[webrobot] loaded ${plugins.size} plugin(s): " +
        plugins.map(p => s"${p.pluginId()} (${p.commands().size()} cmd)").mkString(", "))
  }

  private def discoverJars(): Seq[File] = {
    if (!pluginsDir.isDirectory) return Seq.empty
    Option(pluginsDir.listFiles(new java.io.FilenameFilter {
      def accept(dir: File, name: String): Boolean = name.endsWith(".jar")
    }))
      .map(_.toSeq)
      .getOrElse(Seq.empty)
  }
}
