package org.webrobot.cli.manifest

import com.fasterxml.jackson.databind.ObjectMapper
import eu.webrobot.openapi.client.ApiClient
import org.webrobot.cli.openapi.OpenApiHttp

import java.io.{File, FileWriter}
import java.nio.file.{Files, Paths}
import java.time.Instant
import scala.collection.JavaConverters._
import scala.util.Try

/**
 * Loads and queries the stage catalog.
 *
 * Priority:
 *   1. Remote catalog from GET /webrobot/api/catalog/stages (Strapi-backed, includes plugin stages)
 *      → cached to ~/.webrobot/stage-cache.json with 1-hour TTL
 *   2. Bundled stage-catalog.json (static fallback, always available offline)
 *
 * The actions catalog (browser automation actions) is always read from the
 * bundled stage-catalog.json since actions don't come from plugins.
 */
object StageCatalog {

  private val mapper = new ObjectMapper()

  // ── Bundled static catalog ────────────────────────────────────────────────

  private lazy val bundledRoot: java.util.Map[String, AnyRef] = {
    val is = getClass.getClassLoader.getResourceAsStream("stage-catalog.json")
    if (is == null) throw new RuntimeException("stage-catalog.json non trovato nel classpath")
    mapper.readValue(is, classOf[java.util.Map[String, AnyRef]])
  }

  private lazy val bundledStages: List[Map[String, AnyRef]] = {
    val stages = bundledRoot.get("stages").asInstanceOf[java.util.List[java.util.Map[String, AnyRef]]]
    if (stages == null) List.empty else stages.asScala.map(_.asScala.toMap).toList
  }

  private lazy val actionsCatalog: List[Map[String, AnyRef]] = {
    val actions = bundledRoot.get("actions")
    actions match {
      case l: java.util.List[_] =>
        l.asInstanceOf[java.util.List[java.util.Map[String, AnyRef]]].asScala.map(_.asScala.toMap).toList
      case _ => List.empty
    }
  }

  // ── Remote cache ──────────────────────────────────────────────────────────

  private val cacheDir  = Paths.get(System.getProperty("user.home"), ".webrobot")
  private val cacheFile = cacheDir.resolve("stage-cache.json")
  private val cacheTtlSeconds = 3600L // 1 hour

  private case class CacheEntry(stages: List[Map[String, AnyRef]], fetchedAt: Long)
  private var memCache: Option[CacheEntry] = None

  /** Fetch remote catalog from Jersey and update disk + memory cache. */
  def fetchRemote(apiClient: ApiClient): Boolean = {
    try {
      val node = OpenApiHttp.getJson(apiClient, "/webrobot/api/catalog/stages")
      if (node == null || !node.has("data")) return false
      val dataArr = node.get("data")
      if (!dataArr.isArray) return false

      val stages = dataArr.elements().asScala.map { item =>
        val m = scala.collection.mutable.Map[String, AnyRef]()
        // Map Strapi fields to the same shape used by the bundled catalog
        val stageName = Option(item.get("stage_name")).map(_.asText("")).getOrElse("")
        m("name")          = stageName
        m("extensionType") = Option(item.get("plugin_type")).map(_.asText("")).getOrElse("transformation")
        m("category")      = Option(item.get("plugin_id")).map(_.asText("")).getOrElse("plugin")
        m("description")   = Option(item.get("description")).map(_.asText("")).getOrElse("")
        // Keep raw Strapi fields too so describe can show arg_schema + usage_guide
        if (item.has("aliases"))    m("aliases")    = mapper.convertValue(item.get("aliases"), classOf[java.util.List[AnyRef]])
        if (item.has("arg_schema")) m("args")       = mapper.convertValue(item.get("arg_schema"), classOf[java.util.List[AnyRef]])
        if (item.has("usage_guide") && !item.get("usage_guide").isNull)
                                    m("example")    = item.get("usage_guide").asText("")
        m.toMap
      }.toList

      val entry = CacheEntry(stages, Instant.now().getEpochSecond)
      memCache = Some(entry)

      // Persist to disk
      Try {
        Files.createDirectories(cacheDir)
        val cacheData = Map("fetchedAt" -> entry.fetchedAt, "stages" -> stages.map(_.asJava).asJava)
        val fw = new FileWriter(cacheFile.toFile)
        try mapper.writeValue(fw, cacheData.asJava)
        finally fw.close()
      }

      true
    } catch {
      case _: Exception => false
    }
  }

  /** Load cached remote catalog if still fresh, otherwise None. */
  private def loadCache(): Option[List[Map[String, AnyRef]]] = {
    // Memory cache first
    memCache.filter(e => Instant.now().getEpochSecond - e.fetchedAt < cacheTtlSeconds)
      .map(_.stages)
      .orElse {
        // Disk cache
        Try {
          if (!cacheFile.toFile.exists()) return None
          val root = mapper.readValue(cacheFile.toFile, classOf[java.util.Map[String, AnyRef]])
          val fetchedAt = root.get("fetchedAt").asInstanceOf[Number].longValue()
          if (Instant.now().getEpochSecond - fetchedAt >= cacheTtlSeconds) return None
          val stagesRaw = root.get("stages").asInstanceOf[java.util.List[java.util.Map[String, AnyRef]]]
          val stages = if (stagesRaw == null) List.empty[Map[String, AnyRef]]
                       else stagesRaw.asScala.map(_.asScala.toMap).toList
          memCache = Some(CacheEntry(stages, fetchedAt))
          Some(stages)
        }.getOrElse(None)
      }
  }

  /** Active catalog: remote cache if fresh, else bundled. */
  private def activeCatalog: List[Map[String, AnyRef]] =
    loadCache().getOrElse(bundledStages)

  /** Invalidate disk and memory cache (forces next list/find to hit remote). */
  def invalidateCache(): Unit = {
    memCache = None
    Try(Files.deleteIfExists(cacheFile))
  }

  def cacheAge(): Option[Long] = {
    memCache.map(e => Instant.now().getEpochSecond - e.fetchedAt)
      .orElse(Try {
        if (!cacheFile.toFile.exists()) return None
        val root = mapper.readValue(cacheFile.toFile, classOf[java.util.Map[String, AnyRef]])
        val fetchedAt = root.get("fetchedAt").asInstanceOf[Number].longValue()
        Some(Instant.now().getEpochSecond - fetchedAt)
      }.getOrElse(None))
  }

  def isUsingRemote: Boolean = loadCache().isDefined

  // ── Public API ────────────────────────────────────────────────────────────

  def list(category: Option[String] = None, extensionType: Option[String] = None, search: Option[String] = None): List[Map[String, AnyRef]] =
    activeCatalog
      .filter(s => category.forall(c => s.get("category").exists(_.toString.equalsIgnoreCase(c))))
      .filter(s => extensionType.forall(t => s.get("extensionType").exists(_.toString.equalsIgnoreCase(t))))
      .filter(s => search.forall { q =>
        val lq = q.toLowerCase
        s.values.exists(v => v != null && v.toString.toLowerCase.contains(lq))
      })

  def find(name: String): Option[Map[String, AnyRef]] = {
    val norm = normalize(name)
    activeCatalog.find { s =>
      normalize(s.getOrElse("name", "").toString) == norm ||
      s.get("aliases").exists {
        case l: java.util.List[_] => l.asScala.exists(a => normalize(a.toString) == norm)
        case _ => false
      }
    }
  }

  def exists(name: String): Boolean = find(name).isDefined

  def resolveBase(stageName: String): String =
    if (stageName.contains(":")) stageName.split(":")(0) else stageName

  def categories: List[String] = activeCatalog.flatMap(_.get("category")).map(_.toString).distinct.sorted

  def extensionTypes: List[String] = activeCatalog.flatMap(_.get("extensionType")).map(_.toString).distinct.sorted

  // ── Actions catalog (always bundled) ──────────────────────────────────────

  def listActions(search: Option[String] = None): List[Map[String, AnyRef]] =
    actionsCatalog.filter(a => search.forall { q =>
      val lq = q.toLowerCase
      a.values.exists(v => v != null && v.toString.toLowerCase.contains(lq))
    })

  def findAction(name: String): Option[Map[String, AnyRef]] = {
    val norm = normalize(name)
    actionsCatalog.find(a => normalize(a.getOrElse("name", "").toString) == norm)
  }

  private def normalize(s: String): String = s.toLowerCase.replace("_", "").replace("-", "")
}
