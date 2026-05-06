package org.webrobot.cli.commands

import eu.webrobot.cli.sdk.WebroCliPlugin
import picocli.CommandLine.Command

import java.io.File
import java.net.URLClassLoader
import java.nio.file.{Files, Path, Paths}
import java.util.ServiceLoader
import scala.collection.JavaConverters._

@Command(
  name = "list",
  description = Array("List CLI plugin JARs installed in ~/.webrobot/plugins/.")
)
class CliPluginsList extends Runnable {

  private val pluginsDir: Path = Paths.get(System.getProperty("user.home"), ".webrobot", "plugins")

  override def run(): Unit = {
    if (!Files.isDirectory(pluginsDir)) {
      System.out.println(s"(no plugins directory at $pluginsDir)")
      return
    }

    val jars = Option(pluginsDir.toFile.listFiles(new java.io.FilenameFilter {
      def accept(d: File, n: String): Boolean = n.endsWith(".jar")
    })).map(_.toSeq).getOrElse(Seq.empty)

    if (jars.isEmpty) {
      System.out.println(s"(no plugins installed in $pluginsDir)")
      return
    }

    System.out.println(f"${"PLUGIN"}%-25s ${"DESCRIPTION"}%-50s ${"FILE"}%s")
    System.out.println("-" * 110)

    jars.foreach { jar =>
      try {
        val cl      = new URLClassLoader(Array(jar.toURI.toURL), Thread.currentThread().getContextClassLoader())
        val plugins = ServiceLoader.load(classOf[WebroCliPlugin], cl).asScala.toSeq
        if (plugins.isEmpty) {
          System.out.println(f"${"(unknown)"}%-25s ${"no WebroCliPlugin service registered"}%-50s ${jar.getName}%s")
        } else {
          plugins.foreach { p =>
            val desc = Option(p.description()).getOrElse("")
            System.out.println(f"${p.pluginId()}%-25s ${desc.take(50)}%-50s ${jar.getName}%s")
          }
        }
      } catch {
        case e: Throwable =>
          System.out.println(f"${"(error)"}%-25s ${e.getMessage.take(50)}%-50s ${jar.getName}%s")
      }
    }
  }
}
