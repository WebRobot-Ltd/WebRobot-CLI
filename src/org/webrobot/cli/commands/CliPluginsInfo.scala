package org.webrobot.cli.commands

import eu.webrobot.cli.sdk.WebroCliPlugin
import picocli.CommandLine.{Command, Parameters}

import java.io.File
import java.net.URLClassLoader
import java.nio.file.Paths
import java.util.ServiceLoader
import scala.collection.JavaConverters._

@Command(
  name = "info",
  description = Array("Show metadata for an installed CLI plugin.")
)
class CliPluginsInfo extends Runnable {

  @Parameters(paramLabel = "PLUGIN_ID", description = Array("pluginId from `webrobot cli plugins list`"))
  var pluginId: String = _

  override def run(): Unit = {
    val pluginsDir = Paths.get(System.getProperty("user.home"), ".webrobot", "plugins").toFile
    if (!pluginsDir.isDirectory) { System.err.println("No plugins directory."); sys.exit(1) }

    val jars = Option(pluginsDir.listFiles(new java.io.FilenameFilter {
      def accept(d: File, n: String): Boolean = n.endsWith(".jar")
    })).map(_.toSeq).getOrElse(Seq.empty)

    val found = jars.flatMap { jar =>
      try {
        val cl = new URLClassLoader(Array(jar.toURI.toURL), Thread.currentThread().getContextClassLoader())
        ServiceLoader.load(classOf[WebroCliPlugin], cl).asScala.toSeq
          .filter(_.pluginId() == pluginId)
          .map(p => (jar, p, p.commands().asScala.toSeq))
      } catch { case _: Throwable => Seq.empty }
    }

    if (found.isEmpty) { System.err.println(s"No plugin '$pluginId' installed."); sys.exit(1) }

    found.foreach { case (jar, plugin, commands) =>
      System.out.println(s"pluginId    : ${plugin.pluginId()}")
      System.out.println(s"description : ${plugin.description()}")
      System.out.println(s"jar         : ${jar.getAbsolutePath}")
      System.out.println(s"size        : ${jar.length()} bytes")
      System.out.println(s"commands    :")
      commands.foreach { c => System.out.println(s"  - ${c.getName}") }
      System.out.println()
    }
  }
}
