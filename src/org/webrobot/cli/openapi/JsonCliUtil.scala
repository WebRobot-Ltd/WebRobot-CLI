package org.webrobot.cli.openapi

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import org.webrobot.cli.utils.DataGrid

import scala.collection.JavaConverters._

object JsonCliUtil {

  def text(n: JsonNode, field: String): String =
    if (n == null || n.isNull || !n.has(field) || n.get(field).isNull) ""
    else n.get(field).asText("")

  /** Estrae URL da risposte tipo `{ "result": "https://..." }`, `stringResult.result`, o stringa pura. */
  def extractDownloadUrl(n: JsonNode): Option[String] = {
    if (n == null || n.isNull) None
    else if (n.isTextual) Some(n.asText)
    else {
      val nested = Option(n.get("stringResult"))
        .filter(!_.isNull)
        .flatMap(sr => Option(sr.get("result")).filter(!_.isNull).map(_.asText).filter(_.nonEmpty))
      if (nested.isDefined) nested
      else {
        val keys = Seq("url", "result", "downloadUrl", "presignedUrl", "signedUrl")
        keys.view.map(k => Option(n.get(k)).filter(!_.isNull).map(_.asText)).find(_.isDefined).flatten
      }
    }
  }

  def renderArrayGrid(node: JsonNode, columns: String*): Unit = {
    val arr: ArrayNode = node match {
      case a: ArrayNode => a
      case o if o.has("items") && o.get("items").isArray => o.get("items").asInstanceOf[ArrayNode]
      case o if o.has("datasets") && o.get("datasets").isArray => o.get("datasets").asInstanceOf[ArrayNode]
      case o if o.has("jobs") && o.get("jobs").isArray => o.get("jobs").asInstanceOf[ArrayNode]
      case o if o.has("agents") && o.get("agents").isArray => o.get("agents").asInstanceOf[ArrayNode]
      case o if o.has("projects") && o.get("projects").isArray => o.get("projects").asInstanceOf[ArrayNode]
      case o if o.isArray => o.asInstanceOf[ArrayNode]
      case _ =>
        val a = JsonNodeFactory.instance.arrayNode()
        if (node != null && !node.isNull) a.add(node)
        a
    }
    val dg = new DataGrid(columns: _*)
    arr.elements().asScala.foreach { el =>
      val row = columns.map(c => text(el, c))
      dg.add(row: _*)
    }
    if (dg.size > 0) {
      dg.render
      System.out.println(dg.size + " rows in set\n")
    } else System.out.println("Empty set\n")
  }

  def printJson(node: JsonNode): Unit =
    if (node == null || node.isNull) System.out.println("(null)")
    else System.out.println(node.toPrettyString)
}
