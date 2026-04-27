package org.webrobot.cli.manifest

import com.fasterxml.jackson.databind.ObjectMapper
import scala.collection.JavaConverters._

/** Carica e interroga il catalogo stage bundlato in resources/stage-catalog.json. */
object StageCatalog {

  private val mapper = new ObjectMapper()

  private lazy val catalog: List[Map[String, AnyRef]] = {
    val is = getClass.getClassLoader.getResourceAsStream("stage-catalog.json")
    if (is == null) throw new RuntimeException("stage-catalog.json non trovato nel classpath")
    val root = mapper.readValue(is, classOf[java.util.Map[String, AnyRef]])
    val stages = root.get("stages").asInstanceOf[java.util.List[java.util.Map[String, AnyRef]]]
    if (stages == null) List.empty
    else stages.asScala.map(_.asScala.toMap).toList
  }

  def list(category: Option[String] = None, extensionType: Option[String] = None, search: Option[String] = None): List[Map[String, AnyRef]] =
    catalog
      .filter(s => category.forall(c => s.get("category").exists(_.toString.equalsIgnoreCase(c))))
      .filter(s => extensionType.forall(t => s.get("extensionType").exists(_.toString.equalsIgnoreCase(t))))
      .filter(s => search.forall { q =>
        val lq = q.toLowerCase
        s.values.exists(v => v != null && v.toString.toLowerCase.contains(lq))
      })

  def find(name: String): Option[Map[String, AnyRef]] = {
    val norm = normalize(name)
    catalog.find { s =>
      normalize(s.getOrElse("name", "").toString) == norm ||
      s.get("aliases").exists {
        case l: java.util.List[_] => l.asScala.exists(a => normalize(a.toString) == norm)
        case _ => false
      }
    }
  }

  def exists(name: String): Boolean = find(name).isDefined

  // strip colon subtype before lookup (python_row_transform:price_normalizer -> python_row_transform)
  def resolveBase(stageName: String): String =
    if (stageName.contains(":")) stageName.split(":")(0) else stageName

  private def normalize(s: String): String = s.toLowerCase.replace("_", "")

  def categories: List[String] = catalog.flatMap(_.get("category")).map(_.toString).distinct.sorted

  def extensionTypes: List[String] = catalog.flatMap(_.get("extensionType")).map(_.toString).distinct.sorted
}
