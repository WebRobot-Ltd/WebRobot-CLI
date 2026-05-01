package org.webrobot.cli.commands

import com.fasterxml.jackson.databind.JsonNode
import org.webrobot.cli.openapi.OpenApiHttp
import picocli.CommandLine.{Command, Option => Opt, Parameters}

import java.io.File
import java.net.{HttpURLConnection, URL}
import java.nio.file.Files
import scala.collection.JavaConverters._

// ── root command ──────────────────────────────────────────────────────────────

@Command(
  name = "ean",
  mixinStandardHelpOptions = true,
  description = Array("EAN image sourcing — upload, execute, schedule, query."),
  subcommands = Array(
    classOf[EanInfoCommand],
    classOf[EanBootstrapStatusCommand],
    classOf[EanBootstrapOrgCommand],
    classOf[EanStatusCommand],
    classOf[EanUploadCommand],
    classOf[EanExecuteCommand],
    classOf[EanScheduleCommand],
    classOf[EanQueryCommand],
    classOf[EanImagesCommand]
  )
)
class RunEanCommand extends BaseSubCommand {
  override def run(): Unit = new picocli.CommandLine(this).usage(System.out)
}

// ── shared base ───────────────────────────────────────────────────────────────

abstract class EanBaseCommand extends BaseSubCommand {

  protected def baseUrl(): String = {
    val b = apiClient().getBasePath
    if (b.endsWith("/")) b.dropRight(1) else b
  }

  protected def addAuthHeaders(conn: HttpURLConnection): Unit = {
    val auth = generateAuthHeader()
    if (auth != null && auth.nonEmpty) conn.setRequestProperty("Authorization", auth)
    val key = generateApiKeyHeader()
    if (key != null && key.nonEmpty) conn.setRequestProperty("X-API-Key", key)
  }

  protected def postJsonRaw(path: String, body: AnyRef,
                             extraHeaders: Map[String, String] = Map.empty): JsonNode = {
    val mapper = apiClient().getObjectMapper
    val json   = mapper.writeValueAsBytes(body)
    val conn   = new URL(s"${baseUrl()}$path").openConnection().asInstanceOf[HttpURLConnection]
    conn.setDoOutput(true)
    conn.setRequestMethod("POST")
    conn.setRequestProperty("Content-Type", "application/json")
    conn.setConnectTimeout(30000)
    conn.setReadTimeout(120000)
    addAuthHeaders(conn)
    extraHeaders.foreach { case (k, v) => conn.setRequestProperty(k, v) }
    conn.getOutputStream.write(json)
    conn.getOutputStream.close()
    val code = conn.getResponseCode
    val resp = new String(
      (if (code < 400) conn.getInputStream else conn.getErrorStream).readAllBytes(), "UTF-8")
    mapper.readTree(resp)
  }

  protected def printNode(node: JsonNode): Unit = {
    if (node == null) { println("(empty response)"); return }
    println(apiClient().getObjectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(node))
  }
}

// ── info ─────────────────────────────────────────────────────────────────────

@Command(name = "info", mixinStandardHelpOptions = true,
  description = Array("Show plugin info and supported countries."))
class EanInfoCommand extends EanBaseCommand {
  override def startRun(): Unit = {
    init()
    printNode(OpenApiHttp.getJson(apiClient(), "/webrobot/api/ean-image-sourcing/info"))
  }
}

// ── bootstrap status ──────────────────────────────────────────────────────────

@Command(name = "bootstrap-status", mixinStandardHelpOptions = true,
  description = Array("Show bootstrap status for all countries."))
class EanBootstrapStatusCommand extends EanBaseCommand {
  override def startRun(): Unit = {
    init()
    printNode(OpenApiHttp.getJson(apiClient(), "/webrobot/api/ean-image-sourcing/bootstrap/status"))
  }
}

// ── bootstrap org ─────────────────────────────────────────────────────────────

@Command(name = "bootstrap-org", mixinStandardHelpOptions = true,
  description = Array("Bootstrap projects/agents for an organization (super_admin)."))
class EanBootstrapOrgCommand extends EanBaseCommand {

  @Parameters(index = "0", description = Array("Organization ID"))
  var organizationId: String = _

  @Opt(names = Array("--countries"),
    description = Array("Comma-separated countries to bootstrap (default: all)"))
  var countries: String = _

  @Opt(names = Array("--no-pyspark"), description = Array("Skip PySpark code regeneration"))
  var noPyspark: Boolean = false

  override def startRun(): Unit = {
    init()
    val body = new java.util.HashMap[String, AnyRef]()
    if (countries != null && countries.nonEmpty)
      body.put("countries", countries.split(",").map(_.trim).toList.asJava)
    body.put("regeneratePySpark", Boolean.box(!noPyspark))
    printNode(OpenApiHttp.postJson(apiClient(),
      s"/webrobot/api/ean-image-sourcing/bootstrap/organization/$organizationId", body))
  }
}

// ── status ────────────────────────────────────────────────────────────────────

@Command(name = "status", mixinStandardHelpOptions = true,
  description = Array("Get job/pipeline status for a country."))
class EanStatusCommand extends EanBaseCommand {

  @Parameters(index = "0", description = Array("Country (e.g. italy, germany, france)"))
  var country: String = _

  override def startRun(): Unit = {
    init()
    printNode(OpenApiHttp.getJson(apiClient(),
      s"/webrobot/api/ean-image-sourcing/$country/status"))
  }
}

// ── upload ────────────────────────────────────────────────────────────────────

@Command(name = "upload", mixinStandardHelpOptions = true,
  description = Array("Upload a CSV file with EAN codes for a country."))
class EanUploadCommand extends EanBaseCommand {

  @Parameters(index = "0", description = Array("Country (e.g. italy, germany, france)"))
  var country: String = _

  @Opt(names = Array("--file", "-f"), required = true, description = Array("Path to CSV file"))
  var file: File = _

  @Opt(names = Array("--org-code"), description = Array("Organization code (optional)"))
  var orgCode: String = _

  override def startRun(): Unit = {
    init()
    require(file.exists(), s"File not found: ${file.getAbsolutePath}")
    val fileBytes = Files.readAllBytes(file.toPath)
    printNode(uploadCsvMultipart(country, file.getName, fileBytes))
  }

  private def uploadCsvMultipart(country: String, filename: String, data: Array[Byte]): JsonNode = {
    val boundary = "----WebRobotCli" + System.currentTimeMillis()
    val buf = new java.io.ByteArrayOutputStream()

    def writeFile(name: String, fname: String, bytes: Array[Byte]): Unit = {
      buf.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"" + name + "\"; filename=\"" + fname + "\"\r\nContent-Type: text/csv\r\n\r\n").getBytes("UTF-8"))
      buf.write(bytes)
      buf.write("\r\n".getBytes("UTF-8"))
    }

    writeFile("file", filename, data)
    buf.write(s"--$boundary--\r\n".getBytes("UTF-8"))

    var urlStr = s"${baseUrl()}/webrobot/api/ean-image-sourcing/$country/upload"
    if (orgCode != null && orgCode.nonEmpty) urlStr += s"?organization_code=$orgCode"

    val conn = new URL(urlStr).openConnection().asInstanceOf[HttpURLConnection]
    conn.setDoOutput(true)
    conn.setRequestMethod("POST")
    conn.setRequestProperty("Content-Type", s"multipart/form-data; boundary=$boundary")
    conn.setConnectTimeout(30000)
    conn.setReadTimeout(60000)
    addAuthHeaders(conn)
    conn.getOutputStream.write(buf.toByteArray)
    conn.getOutputStream.close()

    val code = conn.getResponseCode
    val resp = new String(
      (if (code < 400) conn.getInputStream else conn.getErrorStream).readAllBytes(), "UTF-8")
    apiClient().getObjectMapper.readTree(resp)
  }
}

// ── execute ───────────────────────────────────────────────────────────────────

@Command(name = "execute", mixinStandardHelpOptions = true,
  description = Array("Execute an image sourcing job immediately."))
class EanExecuteCommand extends EanBaseCommand {

  @Parameters(index = "0", description = Array("Country (e.g. italy, germany, france)"))
  var country: String = _

  @Opt(names = Array("--dataset-id"), description = Array("Dataset ID (default: latest for country)"))
  var datasetId: String = _

  @Opt(names = Array("--credential-id"), description = Array("Cloud credential ID (Google Search)"))
  var credentialId: String = _

  @Opt(names = Array("--credential-ids"), description = Array("Comma-separated credential IDs"))
  var credentialIds: String = _

  @Opt(names = Array("--encryption-key"), description = Array("X-Encryption-Key header value"))
  var encryptionKey: String = _

  @Opt(names = Array("--follow"), description = Array("Poll until job completes"))
  var follow: Boolean = false

  override def startRun(): Unit = {
    init()
    val body = new java.util.HashMap[String, AnyRef]()
    if (datasetId != null)    body.put("datasetId", datasetId)
    if (credentialId != null) body.put("cloudCredentialId", credentialId)
    if (credentialIds != null && credentialIds.nonEmpty)
      body.put("cloudCredentialIds", credentialIds.split(",").map(_.trim).toList.asJava)

    val extraHeaders = if (encryptionKey != null && encryptionKey.nonEmpty)
      Map("X-Encryption-Key" -> encryptionKey) else Map.empty[String, String]

    val node = postJsonRaw(s"/webrobot/api/ean-image-sourcing/$country/execute", body, extraHeaders)
    printNode(node)

    if (follow && node != null) {
      val executionId = Option(node.path("executionId").asText("")).filter(_.nonEmpty)
        .orElse(Option(node.path("jobExecutionId").asText("")).filter(_.nonEmpty))
      val projectId   = Option(node.path("projectId").asText("")).filter(_.nonEmpty)
      val jobId       = Option(node.path("jobId").asText("")).filter(_.nonEmpty)
      (executionId, projectId, jobId) match {
        case (Some(eid), Some(pid), Some(jid)) => followExecution(pid, jid, eid)
        case _ =>
          println(s"  ${ANSI_YELLOW}--follow: IDs esecuzione non trovati nella risposta.${ANSI_RESET}")
      }
    }
  }
}

// ── schedule ──────────────────────────────────────────────────────────────────

@Command(name = "schedule", mixinStandardHelpOptions = true,
  description = Array("Schedule a recurring image sourcing job (cron)."))
class EanScheduleCommand extends EanBaseCommand {

  @Parameters(index = "0", description = Array("Country (e.g. italy, germany, france)"))
  var country: String = _

  @Opt(names = Array("--cron"), required = true,
    description = Array("Cron expression, e.g. \"0 2 * * *\" (daily at 02:00)"))
  var cron: String = _

  @Opt(names = Array("--credential-id"), required = true,
    description = Array("Cloud credential ID (Google Search)"))
  var credentialId: String = _

  @Opt(names = Array("--dataset-id"), description = Array("Dataset ID (optional)"))
  var datasetId: String = _

  @Opt(names = Array("--timezone"), description = Array("Timezone (default: UTC)"), defaultValue = "UTC")
  var timezone: String = _

  @Opt(names = Array("--encryption-key"), description = Array("X-Encryption-Key header value"))
  var encryptionKey: String = _

  override def startRun(): Unit = {
    init()
    val body = new java.util.HashMap[String, AnyRef]()
    body.put("cloudCredentialId", credentialId)
    body.put("schedule", cron)
    body.put("timezone", timezone)
    if (datasetId != null) body.put("datasetId", datasetId)

    val extraHeaders = if (encryptionKey != null && encryptionKey.nonEmpty)
      Map("X-Encryption-Key" -> encryptionKey) else Map.empty[String, String]

    printNode(postJsonRaw(s"/webrobot/api/ean-image-sourcing/$country/schedule", body, extraHeaders))
  }
}

// ── query ─────────────────────────────────────────────────────────────────────

@Command(name = "query", mixinStandardHelpOptions = true,
  description = Array("Query sourced images by EAN codes (full result with metadata)."))
class EanQueryCommand extends EanBaseCommand {

  @Parameters(index = "0", description = Array("Country"))
  var country: String = _

  @Opt(names = Array("--ean", "-e"), required = true, split = ",",
    description = Array("EAN code(s), comma-separated"))
  var eanCodes: Array[String] = _

  @Opt(names = Array("--limit"), description = Array("Max results"))
  var limit: Int = 0

  @Opt(names = Array("--date-from"), description = Array("Filter from date (YYYY-MM-DD)"))
  var dateFrom: String = _

  @Opt(names = Array("--date-to"), description = Array("Filter to date (YYYY-MM-DD)"))
  var dateTo: String = _

  @Opt(names = Array("--confidence"), defaultValue = "0.8",
    description = Array("Min confidence threshold 0.0–1.0 (default 0.8)"))
  var confidence: Double = 0.8

  @Opt(names = Array("--org-code"), description = Array("Organization code (optional)"))
  var orgCode: String = _

  override def startRun(): Unit = {
    init()
    val body = new java.util.HashMap[String, AnyRef]()
    body.put("eanCodes", eanCodes.toList.asJava)
    body.put("confidenceThreshold", Double.box(confidence))
    if (limit > 0)        body.put("limit", Int.box(limit))
    if (dateFrom != null)  body.put("dateFrom", dateFrom)
    if (dateTo != null)    body.put("dateTo", dateTo)

    var path = s"/webrobot/api/ean-image-sourcing/$country/query"
    if (orgCode != null && orgCode.nonEmpty) path += s"?organization_code=$orgCode"
    printNode(OpenApiHttp.postJson(apiClient(), path, body))
  }
}

// ── images ────────────────────────────────────────────────────────────────────

@Command(name = "images", mixinStandardHelpOptions = true,
  description = Array("Get candidate images in simplified format (base64 + score + url)."))
class EanImagesCommand extends EanBaseCommand {

  @Parameters(index = "0", description = Array("Country"))
  var country: String = _

  @Opt(names = Array("--ean", "-e"), required = true, split = ",",
    description = Array("EAN code(s), comma-separated"))
  var eanCodes: Array[String] = _

  @Opt(names = Array("--limit"), defaultValue = "10",
    description = Array("Max images per EAN (default 10)"))
  var limit: Int = 10

  @Opt(names = Array("--date-from"), description = Array("Filter from date (YYYY-MM-DD)"))
  var dateFrom: String = _

  @Opt(names = Array("--date-to"), description = Array("Filter to date (YYYY-MM-DD)"))
  var dateTo: String = _

  @Opt(names = Array("--confidence"), defaultValue = "0.8",
    description = Array("Min confidence threshold 0.0–1.0 (default 0.8)"))
  var confidence: Double = 0.8

  @Opt(names = Array("--org-code"), description = Array("Organization code (optional)"))
  var orgCode: String = _

  override def startRun(): Unit = {
    init()
    val body = new java.util.HashMap[String, AnyRef]()
    body.put("eanCodes", eanCodes.toList.asJava)
    body.put("limit", Int.box(limit))
    body.put("confidenceThreshold", Double.box(confidence))
    if (dateFrom != null) body.put("dateFrom", dateFrom)
    if (dateTo != null)   body.put("dateTo", dateTo)

    var path = s"/webrobot/api/ean-image-sourcing/$country/images"
    if (orgCode != null && orgCode.nonEmpty) path += s"?organization_code=$orgCode"
    printNode(OpenApiHttp.postJson(apiClient(), path, body))
  }
}
