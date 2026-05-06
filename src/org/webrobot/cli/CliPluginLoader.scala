package org.webrobot.cli

import eu.webrobot.cli.sdk.{WebroCliContext, WebroCliPlugin}
import org.slf4j.LoggerFactory
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
 *
 * v0.2: after each plugin is discovered, calls plugin.init(ctx) with a fully
 * configured WebroCliContext so plugin commands can access the API client,
 * config, output formatter and logger without bootstrapping their own.
 * v0.1 plugins keep working — init() is a default no-op on their interface.
 */
object CliPluginLoader {

  private val pluginsDir = new File(System.getProperty("user.home"), ".webrobot/plugins")

  def loadAll(root: CommandLine, ctx: WebroCliContext): Unit = {
    val jars = discoverJars()
    if (jars.isEmpty) return

    val urls        = jars.map(_.toURI.toURL).toArray
    val classLoader = new URLClassLoader(urls, Thread.currentThread().getContextClassLoader())

    val plugins = ServiceLoader.load(classOf[WebroCliPlugin], classLoader).asScala.toSeq

    plugins.foreach { plugin =>
      try plugin.init(ctx)
      catch {
        case e: Exception =>
          System.err.println(s"[webrobot] plugin ${plugin.pluginId()} init failed: ${e.getMessage}")
      }

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

  /** Backward-compat shim for callers that don't yet pass a context. */
  @deprecated("Use loadAll(root, ctx) — v0.2 plugins need the context", "0.2.0")
  def loadAll(root: CommandLine): Unit = loadAll(root, NoOpContext)

  private def discoverJars(): Seq[File] = {
    if (!pluginsDir.isDirectory) return Seq.empty
    Option(pluginsDir.listFiles(new java.io.FilenameFilter {
      def accept(dir: File, name: String): Boolean = name.endsWith(".jar")
    }))
      .map(_.toSeq)
      .getOrElse(Seq.empty)
  }

  /**
   * Minimal no-op context for the deprecated shim. v0.1 plugins ignore it (default no-op),
   * v0.2 plugins that try to use it during init() will throw NPEs — caller should use the
   * ctx-accepting overload.
   */
  private object NoOpContext extends WebroCliContext {
    def api()             = throw new UnsupportedOperationException("no api in compat context")
    def config()          = throw new UnsupportedOperationException("no config in compat context")
    def org()             = throw new UnsupportedOperationException("no org in compat context")
    def output()          = throw new UnsupportedOperationException("no output in compat context")
    def log()             = LoggerFactory.getLogger("webrobot-cli-plugin")
    def pluginDataDir(id: String) = throw new UnsupportedOperationException("no pluginDataDir in compat context")
    def cliVersion()      = "0.0.0-compat"
  }
}
