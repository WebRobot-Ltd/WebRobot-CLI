package org.webrobot.cli.commands

import eu.webrobot.cli.sdk.WebroCliPlugin
import picocli.CommandLine.{Command, Parameters}

import java.io.File
import java.net.URLClassLoader
import java.nio.file.{Files, Paths}
import java.util.ServiceLoader
import scala.collection.JavaConverters._

@Command(
  name = "remove",
  description = Array("Remove an installed CLI plugin by pluginId or jar filename.")
)
class CliPluginsRemove extends Runnable {

  @Parameters(paramLabel = "ID_OR_FILE",
              description = Array("pluginId (e.g. 'sentiment') or jar filename"))
  var target: String = _

  private val pluginsDir = Paths.get(System.getProperty("user.home"), ".webrobot", "plugins")

  override def run(): Unit = {
    if (!Files.isDirectory(pluginsDir)) {
      System.err.println(s"No plugins directory: $pluginsDir")
      return
    }

    val byFilename = pluginsDir.resolve(target)
    if (Files.isRegularFile(byFilename) && target.endsWith(".jar")) {
      Files.delete(byFilename)
      System.out.println(s"Removed: ${byFilename.getFileName}")
      return
    }

    // Resolve by pluginId
    val jars = pluginsDir.toFile.listFiles(new java.io.FilenameFilter {
      def accept(d: File, n: String): Boolean = n.endsWith(".jar")
    })
    if (jars == null || jars.isEmpty) {
      System.err.println("No plugins installed.")
      return
    }

    val matches = jars.filter { jar =>
      try {
        val cl = new URLClassLoader(Array(jar.toURI.toURL), Thread.currentThread().getContextClassLoader())
        ServiceLoader.load(classOf[WebroCliPlugin], cl).asScala.exists(_.pluginId() == target)
      } catch { case _: Throwable => false }
    }

    if (matches.isEmpty) {
      System.err.println(s"No plugin matching '$target' (by id or filename).")
      sys.exit(1)
    }

    matches.foreach { f =>
      f.delete()
      System.out.println(s"Removed: ${f.getName}")
    }
  }
}
