package org.webrobot.cli.commands

import java.io.{ByteArrayOutputStream, FileInputStream}
import java.net.{HttpURLConnection, URL}
import java.nio.charset.StandardCharsets

import eu.webrobot.openapi.client.model.DatasetDto
import org.apache.commons.io.IOUtils
import org.webrobot.cli.openapi.{JsonCliUtil, OpenApiHttp}
import picocli.CommandLine.{Command, Option}

@Command(
  name = "list",
  sortOptions = false,
  description = Array("Elenco dataset (GET /webrobot/api/datasets). Filtri opzionali."),
  footer = Array()
)
class RunListDatasetCommand extends BaseSubCommand {

  @Option(names = Array("-t", "--type"), description = Array("filtro type (query)"))
  private var dsType: String = ""

  @Option(names = Array("--indexed"), description = Array("filtro indexed (query)"))
  private var indexed: String = ""

  @Option(names = Array("-f", "--format"), description = Array("filtro format (query)"))
  private var format: String = ""

  override def startRun(): Unit = {
    this.init()
    val tuples = Seq(
      scala.Option(dsType).filter(_.nonEmpty).map("type" -> _.asInstanceOf[AnyRef]),
      scala.Option(indexed).filter(_.nonEmpty).map("indexed" -> _.asInstanceOf[AnyRef]),
      scala.Option(format).filter(_.nonEmpty).map("format" -> _.asInstanceOf[AnyRef])
    ).flatten
    val qp = OpenApiHttp.pairs(apiClient(), tuples: _*)
    val node = OpenApiHttp.getJson(apiClient(), "/webrobot/api/datasets", qp)
    if (node != null && node.isArray)
      JsonCliUtil.renderArrayGrid(node, "id", "name", "datasetType", "storageType", "format", "enabled", "createdAt")
    else JsonCliUtil.printJson(node)
  }
}

@Command(
  name = "delete",
  sortOptions = false,
  description = Array("Elimina dataset (DELETE /webrobot/api/datasets/{datasetId})."),
  footer = Array()
)
class RunDeleteDatasetCommand extends BaseSubCommand {

  @Option(names = Array("-d", "--datasetId"), description = Array("dataset id"), required = true)
  private var datasetId: String = ""

  override def startRun(): Unit = {
    this.init()
    val path = "/webrobot/api/datasets/" + apiClient().escapeString(datasetId)
    OpenApiHttp.deleteJson(apiClient(), path)
  }
}

@Command(
  name = "get",
  sortOptions = false,
  description = Array("Dettaglio dataset (GET /webrobot/api/datasets/{datasetId})."),
  footer = Array()
)
class RunGetDatasetCommand extends BaseSubCommand {

  @Option(names = Array("-d", "--datasetId"), description = Array("dataset id"), required = true)
  private var datasetId: String = ""

  override def startRun(): Unit = {
    this.init()
    val path = "/webrobot/api/datasets/" + apiClient().escapeString(datasetId)
    val node = OpenApiHttp.getJson(apiClient(), path)
    JsonCliUtil.printJson(node)
  }
}

@Command(
  name = "add",
  sortOptions = false,
  description = Array("Crea dataset (POST /webrobot/api/datasets)."),
  footer = Array(),
  subcommands = Array()
)
class RunAddDatasetCommand extends BaseSubCommand {

  @Option(names = Array("-n", "--name"), description = Array("nome dataset"), required = true)
  private var name: String = ""

  @Option(names = Array("-d", "--description"), description = Array("descrizione"))
  private var description: String = ""

  @Option(names = Array("-p", "--storage-path"), description = Array("path storage (es. s3a://bucket/path)"))
  private var storagePath: String = ""

  @Option(names = Array("-f", "--format"), description = Array("formato: CSV, JSON, PARQUET, XML, TXT"))
  private var format: String = ""

  @Option(names = Array("-t", "--dataset-type"), description = Array("tipo: INPUT, OUTPUT"))
  private var datasetType: String = ""

  @Option(names = Array("-s", "--storage-type"), description = Array("storage backend: INTERNAL_MINIO (default), S3, HETZNER_S3, AZURE_BLOB, GCP_GCS"))
  private var storageType: String = ""

  @Option(names = Array("-c", "--cloud-credential-id"), description = Array("id cloud credential (richiesto se storage-type != INTERNAL_MINIO)"))
  private var cloudCredentialId: String = ""

  override def startRun(): Unit = {
    this.init()
    val dto = new DatasetDto()
    dto.setName(name)
    if (description != null && description.nonEmpty) dto.setDescription(description)
    if (storagePath != null && storagePath.nonEmpty)  dto.setStoragePath(storagePath)
    if (format != null && format.nonEmpty)            dto.setFormat(format.toUpperCase)
    if (datasetType != null && datasetType.nonEmpty)  dto.setDatasetType(datasetType.toUpperCase)
    if (storageType != null && storageType.nonEmpty)  dto.setStorageType(storageType.toUpperCase)
    if (cloudCredentialId != null && cloudCredentialId.nonEmpty) {
      try { dto.setCloudCredentialId(cloudCredentialId.toInt) } catch { case _: NumberFormatException => }
    }
    val node = OpenApiHttp.postJson(apiClient(), "/webrobot/api/datasets", dto)
    JsonCliUtil.printJson(node)
  }
}

@Command(
  name = "upload",
  sortOptions = false,
  description = Array(
    "Upload file dataset (POST multipart /webrobot/api/datasets/upload). Campi: file, datasetType (input|output), name."
  ),
  footer = Array()
)
class RunDatasetUploadCommand extends BaseSubCommand {

  @Option(names = Array("-F", "--file"), description = Array("file locale (csv, …)"), required = true)
  private var filePath: String = ""

  @Option(names = Array("-t", "--datasetType"), description = Array("input oppure output"), required = true)
  private var datasetType: String = ""

  @Option(names = Array("-n", "--name"), description = Array("nome dataset (path MinIO)"), required = true)
  private var datasetName: String = ""

  private def baseUrlNoSlash: String = {
    val b = apiClient().getBasePath
    if (b == null) ""
    else if (b.endsWith("/")) b.substring(0, b.length - 1)
    else b
  }

  private def writeMultipart(out: ByteArrayOutputStream, boundary: String, name: String, value: String): Unit = {
    out.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8))
    out.write(
      ("Content-Disposition: form-data; name=\"" + name + "\"\r\n\r\n" + value + "\r\n").getBytes(StandardCharsets.UTF_8)
    )
  }

  private def writeMultipartFile(
      out: ByteArrayOutputStream,
      boundary: String,
      fieldName: String,
      filename: String,
      contentType: String,
      data: Array[Byte]
  ): Unit = {
    out.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8))
    out.write(
      ("Content-Disposition: form-data; name=\"" + fieldName + "\"; filename=\"" + filename + "\"\r\n").getBytes(
        StandardCharsets.UTF_8
      )
    )
    out.write(("Content-Type: " + contentType + "\r\n\r\n").getBytes(StandardCharsets.UTF_8))
    out.write(data)
    out.write("\r\n".getBytes(StandardCharsets.UTF_8))
  }

  override def startRun(): Unit = {
    this.init()
    val f = new java.io.File(filePath)
    if (!f.isFile) throw new java.io.FileNotFoundException(filePath)
    val bytes = IOUtils.toByteArray(new FileInputStream(f))
    val origName = f.getName
    val boundary = "----WebRobotCli" + System.currentTimeMillis()
    val body = new ByteArrayOutputStream()
    writeMultipartFile(body, boundary, "file", origName, "application/octet-stream", bytes)
    writeMultipart(body, boundary, "datasetType", datasetType)
    writeMultipart(body, boundary, "name", datasetName)
    body.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8))

    val url = new URL(baseUrlNoSlash + "/webrobot/api/datasets/upload")
    val conn = url.openConnection.asInstanceOf[HttpURLConnection]
    conn.setDoOutput(true)
    conn.setRequestMethod("POST")
    conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary)
    val auth = generateAuthHeader()
    if (auth != null && auth.nonEmpty) conn.setRequestProperty("Authorization", auth)
    val key = generateApiKeyHeader()
    if (key != null && key.nonEmpty) conn.setRequestProperty("X-API-Key", key)

    conn.getOutputStream.write(body.toByteArray)
    conn.getOutputStream.close()
    val code = conn.getResponseCode
    val stream = if (code >= 200 && code < 300) conn.getInputStream else conn.getErrorStream
    val resp = IOUtils.toString(stream, StandardCharsets.UTF_8)
    val node = apiClient().getObjectMapper.readTree(resp)
    JsonCliUtil.printJson(node)
  }
}

@Command(
  name = "query",
  sortOptions = false,
  description = Array("Query SQL su catalogo (POST /webrobot/api/datasets/query, PrestoQueryRequest)."),
  footer = Array()
)
class RunDatasetQueryCommand extends BaseSubCommand {

  @Option(names = Array("-q", "--sql"), description = Array("SQL"), required = true)
  private var sql: String = ""

  @Option(names = Array("--catalog"), description = Array("catalog (default minio)"))
  private var catalog: String = "minio"

  @Option(names = Array("--schema"), description = Array("schema (default default)"))
  private var schema: String = "default"

  @Option(names = Array("--limit"), description = Array("limit righe"))
  private var limit: String = ""

  @Option(names = Array("--offset"), description = Array("offset"))
  private var offset: String = ""

  override def startRun(): Unit = {
    this.init()
    val om = apiClient().getObjectMapper
    val body = om.createObjectNode()
    body.put("sql", sql)
    body.put("catalog", catalog)
    body.put("schema", schema)
    if (limit != null && limit.trim.nonEmpty) body.put("limit", limit.trim.toInt)
    if (offset != null && offset.trim.nonEmpty) body.put("offset", offset.trim.toInt)
    val node = OpenApiHttp.postJson(apiClient(), "/webrobot/api/datasets/query", body)
    JsonCliUtil.printJson(node)
  }
}

@Command(
  name = "dataset",
  mixinStandardHelpOptions = true,
  sortOptions = false,
  description = Array("Dataset (REST OpenAPI /webrobot/api/datasets/...)."),
  footer = Array(),
  subcommands = Array(
    classOf[RunListDatasetCommand],
    classOf[RunGetDatasetCommand],
    classOf[RunAddDatasetCommand],
    classOf[RunDeleteDatasetCommand],
    classOf[RunDatasetUploadCommand],
    classOf[RunDatasetQueryCommand]
  )
)
class RunDatasetCommand extends Runnable {

  def run(): Unit = {
    System.err.println(
      "Uso: webrobot dataset <sottocomando>. Sottocomandi: list | get | add | delete | upload | query"
    )
    System.err.println("Esempio: webrobot dataset list")
  }
}
