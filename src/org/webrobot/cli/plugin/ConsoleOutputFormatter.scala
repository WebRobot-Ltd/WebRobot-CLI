package org.webrobot.cli.plugin

import com.fasterxml.jackson.databind.{JsonNode, ObjectMapper}
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import eu.webrobot.cli.sdk.OutputFormatter

import java.io.PrintStream
import scala.collection.JavaConverters._

/**
 * Console output formatter honoring the global --output flag.
 * Defaults to "table"; falls back to compact JSON when input doesn't suit a table.
 */
final class ConsoleOutputFormatter(out: PrintStream, fmt: String) extends OutputFormatter {

  private val mapper      = new ObjectMapper()
  private val yamlMapper  = new ObjectMapper(new YAMLFactory())
  private val prettyJson  = mapper.writerWithDefaultPrettyPrinter()
  private val prettyYaml  = yamlMapper.writerWithDefaultPrettyPrinter()

  private val fmtNorm = Option(fmt).map(_.toLowerCase).getOrElse("table") match {
    case "json" => "json"
    case "yaml" => "yaml"
    case _      => "table"
  }

  override def table(rows: JsonNode): Unit = {
    if (rows == null || rows.isNull) { out.println("(empty)"); return }
    rows match {
      case arr: ArrayNode if arr.size() > 0 =>
        val first = arr.get(0)
        if (first.isObject) printTable(arr) else printList(arr)
      case obj if obj.isObject =>
        // single-object → key/value pairs
        printKv(obj)
      case other =>
        out.println(other.toString)
    }
  }

  override def json(value: JsonNode): Unit = out.println(prettyJson.writeValueAsString(value))

  override def yaml(value: JsonNode): Unit = out.println(prettyYaml.writeValueAsString(value))

  override def println(line: String): Unit = out.println(line)

  override def format(): String = fmtNorm

  // ── helpers ────────────────────────────────────────────────────────────────

  private def printTable(arr: ArrayNode): Unit = {
    val cols = arr.get(0).fieldNames().asScala.toList
    val rows = arr.elements().asScala.toList.map { node =>
      cols.map(c => Option(node.get(c)).map(_.asText("")).getOrElse(""))
    }
    val widths = cols.zipWithIndex.map { case (c, i) =>
      math.max(c.length, rows.map(r => r(i).length).foldLeft(0)(math.max))
    }
    val sep = widths.map(w => "─" * (w + 2)).mkString("┼")
    out.println(cols.zip(widths).map { case (c, w) => " " + pad(c, w) + " " }.mkString("│"))
    out.println(sep)
    rows.foreach { row =>
      out.println(row.zip(widths).map { case (v, w) => " " + pad(v, w) + " " }.mkString("│"))
    }
  }

  private def printKv(obj: JsonNode): Unit = {
    val keys     = obj.fieldNames().asScala.toList
    val keyWidth = keys.map(_.length).foldLeft(0)(math.max)
    keys.foreach { k =>
      val v = Option(obj.get(k)).map(_.asText("")).getOrElse("")
      out.println(pad(k, keyWidth) + " : " + v)
    }
  }

  private def printList(arr: ArrayNode): Unit =
    arr.elements().asScala.foreach(n => out.println(n.asText(n.toString)))

  private def pad(s: String, width: Int): String =
    if (s.length >= width) s else s + (" " * (width - s.length))
}
