package org.webrobot.cli.manifest

import org.yaml.snakeyaml.{DumperOptions, Yaml}
import org.yaml.snakeyaml.DumperOptions.{FlowStyle, ScalarStyle}

import java.io.{File, FileWriter, StringWriter}
import java.nio.file.{Files, Paths}
import scala.collection.JavaConverters._
import scala.collection.mutable

/**
 * Legge e scrive manifest YAML multi-documento.
 *
 * Ogni documento è rappresentato come Map[String, Any] mutabile.
 * La modifica avviene in-memory; `save()` riscrive il file.
 * I commenti NON vengono preservati (trade-off accettato per semplicità).
 */
object YamlManifest {

  private def dumperOptions(): DumperOptions = {
    val opts = new DumperOptions()
    opts.setDefaultFlowStyle(FlowStyle.BLOCK)
    opts.setDefaultScalarStyle(ScalarStyle.PLAIN)
    opts.setIndent(2)
    opts.setPrettyFlow(true)
    opts.setSplitLines(false)
    opts
  }

  /** Carica tutti i documenti YAML da file. */
  def load(file: File): List[mutable.Map[String, Any]] = {
    if (!file.exists()) return List.empty
    val content = new String(Files.readAllBytes(file.toPath), "UTF-8")
    loadString(content)
  }

  def loadString(content: String): List[mutable.Map[String, Any]] = {
    val yaml = new Yaml()
    val docs = yaml.loadAll(content).asScala.toList
    docs.collect {
      case m: java.util.Map[_, _] =>
        deepConvert(m.asInstanceOf[java.util.Map[String, Any]])
      case null => mutable.Map.empty[String, Any]
    }
  }

  /** Salva tutti i documenti nel file con separatore ---. */
  def save(file: File, docs: List[mutable.Map[String, Any]]): Unit = {
    val yaml = new Yaml(dumperOptions())
    val sw = new StringWriter()
    docs.zipWithIndex.foreach { case (doc, i) =>
      if (i > 0) sw.write("\n---\n\n")
      else sw.write("---\n\n")
      yaml.dump(toJava(doc), sw)
    }
    val fw = new FileWriter(file)
    try fw.write(sw.toString)
    finally fw.close()
  }

  /** Trova il primo documento `kind: Pipeline` (o con `spec.pipeline`). */
  def findPipeline(docs: List[mutable.Map[String, Any]]): Option[mutable.Map[String, Any]] =
    docs.find(d => d.get("kind").exists(_.toString == "Pipeline") || d.contains("pipeline"))

  /** Restituisce/crea il documento Pipeline nel file, crea il file se assente. */
  def ensurePipeline(file: File): (List[mutable.Map[String, Any]], mutable.Map[String, Any]) = {
    val docs = if (file.exists()) load(file) else List.empty
    findPipeline(docs) match {
      case Some(pd) => (docs, pd)
      case None =>
        val pd = mutable.Map[String, Any](
          "apiVersion" -> "webrobot/v1",
          "kind" -> "Pipeline",
          "metadata" -> mutable.Map[String, Any]("name" -> file.getName.stripSuffix(".yaml")),
          "spec" -> mutable.Map[String, Any]("pipeline" -> new java.util.ArrayList[Any]())
        )
        (docs :+ pd, pd)
    }
  }

  /** Ritorna la lista stage (come java.util.List mutabile) dal documento Pipeline. */
  def stageList(pipelineDoc: mutable.Map[String, Any]): java.util.List[Any] = {
    // supporta sia formato manifest (spec.pipeline) che formato legacy (pipeline)
    val specOpt = pipelineDoc.get("spec").collect { case m: mutable.Map[_, _] => m.asInstanceOf[mutable.Map[String, Any]] }
    specOpt.flatMap(_.get("pipeline")).orElse(pipelineDoc.get("pipeline")) match {
      case Some(l: java.util.List[_]) => l.asInstanceOf[java.util.List[Any]]
      case _ =>
        val l = new java.util.ArrayList[Any]()
        specOpt match {
          case Some(spec) => spec("pipeline") = l
          case None => pipelineDoc("pipeline") = l
        }
        l
    }
  }

  /** Aggiunge uno stage alla lista pipeline. */
  def addStage(
    pipelineDoc: mutable.Map[String, Any],
    stageName: String,
    args: Option[List[String]],
    params: Map[String, String],
    displayName: Option[String],
    position: StagePosition
  ): Unit = {
    val stages = stageList(pipelineDoc)
    val entry = new java.util.LinkedHashMap[String, Any]()
    entry.put("stage", stageName)
    displayName.foreach(n => entry.put("name", n))
    args match {
      case Some(a) if a.nonEmpty =>
        val al = new java.util.ArrayList[Any]()
        a.foreach { s =>
          val typed: Any =
            if (s == "true") java.lang.Boolean.TRUE
            else if (s == "false") java.lang.Boolean.FALSE
            else try { java.lang.Integer.parseInt(s) }
            catch { case _: NumberFormatException =>
              try { java.lang.Double.parseDouble(s) }
              catch { case _: NumberFormatException => s }
            }
          al.add(typed)
        }
        entry.put("args", al)
      case _ =>
    }
    if (params.nonEmpty) {
      val config = new java.util.LinkedHashMap[String, Any]()
      params.foreach { case (k, v) => config.put(k, v) }
      entry.put("config", config)
    }

    val idx = position match {
      case AtEnd       => stages.size()
      case AtStart     => 0
      case AtIndex(i)  => math.min(i, stages.size())
      case AfterName(n) =>
        val found = (0 until stages.size()).find { i =>
          stages.get(i) match {
            case m: java.util.Map[_, _] => m.asInstanceOf[java.util.Map[String, Any]].get("stage") == n ||
                                           m.asInstanceOf[java.util.Map[String, Any]].get("name") == n
            case _ => false
          }
        }
        found.map(_ + 1).getOrElse(stages.size())
      case BeforeName(n) =>
        val found = (0 until stages.size()).find { i =>
          stages.get(i) match {
            case m: java.util.Map[_, _] => m.asInstanceOf[java.util.Map[String, Any]].get("stage") == n ||
                                           m.asInstanceOf[java.util.Map[String, Any]].get("name") == n
            case _ => false
          }
        }
        found.getOrElse(stages.size())
    }
    stages.add(idx, entry)
  }

  /** Rimuove uno stage per indice (0-based) o per nome stage. */
  def removeStage(pipelineDoc: mutable.Map[String, Any], target: String): Boolean = {
    val stages = stageList(pipelineDoc)
    // tenta come indice
    try {
      val idx = target.toInt
      if (idx >= 0 && idx < stages.size()) { stages.remove(idx); return true }
    } catch { case _: NumberFormatException => }
    // cerca per nome stage
    val found = (0 until stages.size()).find { i =>
      stages.get(i) match {
        case m: java.util.Map[_, _] => m.asInstanceOf[java.util.Map[String, Any]].get("stage").toString.startsWith(target) ||
                                       m.asInstanceOf[java.util.Map[String, Any]].getOrDefault("name", "").toString == target
        case _ => false
      }
    }
    found.foreach(stages.remove)
    found.isDefined
  }

  /** Imposta spec.input nel documento Pipeline. */
  def setInput(pipelineDoc: mutable.Map[String, Any], dataset: Option[String], path: Option[String]): Unit = {
    val spec = getOrCreateSpec(pipelineDoc)
    val input = mutable.Map[String, Any]()
    dataset.foreach(d => input("dataset") = d)
    path.foreach(p => input("path") = p)
    spec("input") = input
  }

  /** Imposta spec.output nel documento Pipeline. */
  def setOutput(pipelineDoc: mutable.Map[String, Any], format: Option[String], mode: Option[String], path: Option[String]): Unit = {
    val spec = getOrCreateSpec(pipelineDoc)
    val out = spec.get("output").collect { case m: mutable.Map[_, _] => m.asInstanceOf[mutable.Map[String, Any]] }
                   .getOrElse(mutable.Map[String, Any]())
    format.foreach(f => out("format") = f)
    mode.foreach(m => out("mode") = m)
    path.foreach(p => out("path") = p)
    spec("output") = out
  }

  /** Imposta spec.schedule nel documento Pipeline. */
  def setSchedule(pipelineDoc: mutable.Map[String, Any], cron: String, timezone: String): Unit = {
    val spec = getOrCreateSpec(pipelineDoc)
    spec("schedule") = mutable.Map[String, Any]("cron" -> cron, "timezone" -> timezone)
  }

  private def getOrCreateSpec(doc: mutable.Map[String, Any]): mutable.Map[String, Any] =
    doc.get("spec").collect { case m: mutable.Map[_, _] => m.asInstanceOf[mutable.Map[String, Any]] } match {
      case Some(s) => s
      case None =>
        val s = mutable.Map[String, Any]()
        doc("spec") = s
        s
    }

  // Converts java.util.Map recursively to mutable.Map[String, Any]
  private def deepConvert(m: java.util.Map[String, Any]): mutable.Map[String, Any] = {
    val result = mutable.Map[String, Any]()
    m.asScala.foreach { case (k, v) => result(k) = convertValue(v) }
    result
  }

  private def convertValue(v: Any): Any = v match {
    case m: java.util.Map[_, _]  => deepConvert(m.asInstanceOf[java.util.Map[String, Any]])
    case l: java.util.List[_]    => l // keep as java.util.List for SnakeYAML round-trip
    case other                   => other
  }

  // Converts mutable.Map back to java.util.LinkedHashMap for SnakeYAML serialization
  private def toJava(m: mutable.Map[String, Any]): java.util.Map[String, Any] = {
    val out = new java.util.LinkedHashMap[String, Any]()
    m.foreach { case (k, v) => out.put(k, toJavaValue(v)) }
    out
  }

  private def toJavaValue(v: Any): Any = v match {
    case m: mutable.Map[_, _] => toJava(m.asInstanceOf[mutable.Map[String, Any]])
    case m: Map[_, _]         => {
      val out = new java.util.LinkedHashMap[String, Any]()
      m.asInstanceOf[Map[String, Any]].foreach { case (k, v2) => out.put(k, toJavaValue(v2)) }
      out
    }
    case l: java.util.List[_] => {
      val out = new java.util.ArrayList[Any]()
      l.asScala.foreach(item => out.add(toJavaValue(item)))
      out
    }
    case s: Seq[_] => {
      val out = new java.util.ArrayList[Any]()
      s.foreach(item => out.add(toJavaValue(item)))
      out
    }
    case other => other
  }
}

sealed trait StagePosition
case object AtEnd                  extends StagePosition
case object AtStart                extends StagePosition
case class  AtIndex(i: Int)        extends StagePosition
case class  AfterName(name: String) extends StagePosition
case class  BeforeName(name: String) extends StagePosition
