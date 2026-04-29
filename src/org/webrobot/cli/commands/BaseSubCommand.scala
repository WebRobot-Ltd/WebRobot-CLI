package org.webrobot.cli.commands

import java.io.{IOException, InputStream}
import java.net.{HttpURLConnection, URL}

import com.fasterxml.jackson.databind.JsonNode
import com.google.gson.Gson
import eu.webrobot.openapi.client.{ApiClient, ApiException}
import org.apache.commons.io.IOUtils
import org.webrobot.cli.RunWebRobotCli
import org.webrobot.cli.commands.models.ErrorException
import org.webrobot.cli.openapi.OpenApiHttp
import org.webrobot.cli.utils.DataGrid
import WebRobot.Cli.Sdk.openapi.OpenApiSdkAdapter

class BaseSubCommand extends Runnable {

  protected var openApi: OpenApiSdkAdapter = _
  protected var dataGrid: DataGrid = _
  protected var errorManager: ErrorManager = new ErrorManager()

  val ANSI_RESET  = "[0m"
  val ANSI_RED    = "[31m"
  val ANSI_GREEN  = "[32m"
  val ANSI_YELLOW = "[33m"
  val ANSI_CYAN   = "[36m"
  val ANSI_BOLD   = "[1m"

  private val TERMINAL_STATES = Set("COMPLETED", "FAILED", "ERROR", "CANCELLED", "CANCELED", "STOPPED", "ABORTED")

  /** Estrae il primo valore non-null trovato tra le chiavi candidate. */
  protected def extractJsonField(node: JsonNode, keys: String*): String = {
    if (node == null) return null
    for (k <- keys) {
      val v = node.get(k)
      if (v != null && !v.isNull && v.asText("").nonEmpty) return v.asText("")
    }
    null
  }

  /** Ricerca ricorsiva di una chiave in un JsonNode (oggetti e array). */
  protected def findJsonField(node: JsonNode, key: String): String = {
    if (node == null || node.isNull) return null
    if (node.isObject) {
      val direct = node.get(key)
      if (direct != null && !direct.isNull && direct.asText("").nonEmpty) return direct.asText("")
      val it = node.fields()
      while (it.hasNext) {
        val r = findJsonField(it.next().getValue, key)
        if (r != null) return r
      }
    } else if (node.isArray) {
      val it = node.elements()
      while (it.hasNext) {
        val r = findJsonField(it.next(), key)
        if (r != null) return r
      }
    }
    null
  }

  /**
   * Attende il completamento di un'esecuzione polling lo stato ogni 5 s.
   * Stampa il progresso sulla stessa riga; riga finale colorata.
   */
  protected def followExecution(projectId: String, jobId: String, executionId: String): Unit = {
    val statusPath =
      "/webrobot/api/projects/id/" + apiClient().escapeString(projectId) +
      "/jobs/"                     + apiClient().escapeString(jobId)      +
      "/executions/"               + apiClient().escapeString(executionId) + "/status"
    System.out.println(s"  ${ANSI_CYAN}execution-id: $executionId${ANSI_RESET}")
    var lastStatus = ""
    var dots       = 0
    var running    = true
    while (running) {
      Thread.sleep(5000)
      try {
        val node   = OpenApiHttp.getJson(apiClient(), statusPath)
        val status = if (node != null) extractJsonField(node, "status", "executionStatus", "state") else ""
        if (status != null && status.nonEmpty && status != lastStatus) {
          if (lastStatus.nonEmpty) System.out.println()
          System.out.print(s"  Stato: ${ANSI_BOLD}$status${ANSI_RESET}")
          System.out.flush()
          lastStatus = status
          dots = 0
        } else if (status != null && status.nonEmpty) {
          dots += 1
          System.out.print("\r  Stato: " + ANSI_BOLD + status + ANSI_RESET + ("." * math.min(dots, 30)))
          System.out.flush()
        }
        if (status != null && TERMINAL_STATES.contains(status.toUpperCase)) {
          System.out.println()
          if ("COMPLETED".equalsIgnoreCase(status))
            System.out.println(s"${ANSI_GREEN}✓ Completata con successo.${ANSI_RESET}")
          else
            System.out.println(s"${ANSI_RED}✗ Terminata: $status${ANSI_RESET}")
          running = false
        }
      } catch {
        case e: Exception =>
          System.out.println(s"\n  ${ANSI_YELLOW}(polling interrotto: ${e.getMessage})${ANSI_RESET}")
      }
    }
  }

  protected def uploadDatasetCsvMultipart(name: String, csvBytes: Array[Byte], datasetType: String = "input"): Option[String] = {
    try {
      val boundary = "----WebRobotCli" + System.currentTimeMillis()
      val body = new java.io.ByteArrayOutputStream()

      def writePart(field: String, value: String): Unit = {
        val s = "--" + boundary + "\r\nContent-Disposition: form-data; name=\"" + field + "\"\r\n\r\n" + value + "\r\n"
        body.write(s.getBytes("UTF-8"))
      }

      def writeFilePart(field: String, filename: String, data: Array[Byte]): Unit = {
        val h = "--" + boundary + "\r\nContent-Disposition: form-data; name=\"" + field + "\"; filename=\"" + filename + "\"\r\nContent-Type: text/csv\r\n\r\n"
        body.write(h.getBytes("UTF-8"))
        body.write(data)
        body.write("\r\n".getBytes("UTF-8"))
      }

      writeFilePart("file", name + ".csv", csvBytes)
      writePart("datasetType", datasetType)
      writePart("name", name)
      body.write(s"--$boundary--\r\n".getBytes("UTF-8"))

      val base = { val b = apiClient().getBasePath; if (b.endsWith("/")) b.dropRight(1) else b }
      val url2 = new URL(base + "/webrobot/api/datasets/upload")
      val conn = url2.openConnection().asInstanceOf[HttpURLConnection]
      conn.setDoOutput(true)
      conn.setRequestMethod("POST")
      conn.setRequestProperty("Content-Type", s"multipart/form-data; boundary=$boundary")
      val auth = generateAuthHeader()
      if (auth != null && auth.nonEmpty) conn.setRequestProperty("Authorization", auth)
      val key = generateApiKeyHeader()
      if (key != null && key.nonEmpty) conn.setRequestProperty("X-API-Key", key)
      conn.getOutputStream.write(body.toByteArray)
      conn.getOutputStream.close()

      val code = conn.getResponseCode
      val stream2 = if (code >= 200 && code < 300) conn.getInputStream else conn.getErrorStream
      val resp = IOUtils.toString(stream2, "UTF-8")
      val node = apiClient().getObjectMapper.readTree(resp)
      val id = node.path("id").asText("")
      if (id.nonEmpty) Some(id) else None
    } catch {
      case e: Exception =>
        System.out.println(s"  ${ANSI_YELLOW}Errore upload dataset: ${e.getMessage}${ANSI_RESET}")
        None
    }
  }

  protected def uploadFile(url: URL, stream: InputStream): Unit = {
    val connection = url.openConnection.asInstanceOf[HttpURLConnection]
    connection.setDoOutput(true)
    connection.setRequestMethod("PUT")
    IOUtils.copy(stream, connection.getOutputStream)
    System.out.println("HTTP response code: " + connection.getResponseCode)
    System.out.println("HTTP response message: " + connection.getResponseMessage)
  }

  protected def apiClient(): ApiClient = openApi.getApiClient

  protected def generateApiKeyHeader(): String = {
    try {
      if (RunWebRobotCli.config != null) {
        if (RunWebRobotCli.config.hasPath("apikey")) {
          val v = RunWebRobotCli.config.getString("apikey")
          if (v != null) return v
        }
        if (RunWebRobotCli.config.hasPath("apiKey")) {
          val v = RunWebRobotCli.config.getString("apiKey")
          if (v != null) return v
        }
      }
    } catch { case _: Throwable => () }
    ""
  }

  protected def generateAuthHeader(): String = {
    try {
      if (RunWebRobotCli.config != null && RunWebRobotCli.config.hasPath("jwt")) {
        val jwt = RunWebRobotCli.config.getString("jwt")
        if (jwt != null && !jwt.isEmpty) return s"Bearer $jwt"
      }
      if (RunWebRobotCli.config != null && RunWebRobotCli.config.hasPath("bearer")) {
        val bearer = RunWebRobotCli.config.getString("bearer")
        if (bearer != null && !bearer.isEmpty) return s"Bearer $bearer"
      }
      if (RunWebRobotCli.config != null && RunWebRobotCli.config.hasPath("token")) {
        val token = RunWebRobotCli.config.getString("token")
        if (token != null && !token.isEmpty) return s"Bearer $token"
      }
    } catch { case _: Throwable => () }

    val plain = generateApiKeyHeader()
    if (plain != null && plain.nonEmpty) return s"ApiKey $plain"

    ""
  }

  protected def resolveApiEndpoint(): String = {
    val defaultEndpoint = "https://api.webrobot.eu"
    try {
      if (RunWebRobotCli.config != null && RunWebRobotCli.config.hasPath("api_endpoint")) {
        val ep = RunWebRobotCli.config.getString("api_endpoint")
        if (ep != null && ep.trim.nonEmpty) return ep.trim
      }
    } catch { case _: Throwable => () }
    defaultEndpoint
  }

  protected def init(): Unit = {
    openApi = new OpenApiSdkAdapter(resolveApiEndpoint())
    val auth = generateAuthHeader()
    if (auth != null && auth.startsWith("Bearer ")) {
      openApi.getApiClient.addDefaultHeader("Authorization", auth)
    } else {
      val key = generateApiKeyHeader()
      if (key != null && key.nonEmpty) {
        openApi.withApiKey("X-API-Key", key)
        openApi.withApiKey("Authorization", s"ApiKey $key")
      }
    }
    dataGrid = new DataGrid()
  }

  def startRun(): Unit = ()

  def run(): Unit = {
    try {
      this.startRun()
      System.err.println(ANSI_GREEN + "OK: the command was successful" + ANSI_RESET)
    } catch {
      case e: IOException =>
        System.out.println(ANSI_RED + "KO: the file is not present in the system" + ANSI_RESET)
        sys.exit(-1)
      case e: ApiException =>
        System.out.println(ANSI_RED + "KO: API " + e.getCode + " — " + e.getMessage + ANSI_RESET)
        sys.exit(-1)
      case e: Exception =>
        var message = e.getMessage
        try {
          val gson = new Gson()
          val errorException = gson.fromJson(message, classOf[ErrorException])
          if (errorException.getException == null) errorException.setException("")
          val errorMessage = errorManager.run(errorException)
          if (!errorMessage.equals("")) System.out.println(ANSI_RED + "KO:" + errorMessage + ANSI_RESET)
          else System.out.println(message)
          sys.exit(-1)
        } catch {
          case _: Exception =>
            System.out.println(ANSI_RED + "KO: generic exception with message:" + message + ANSI_RESET)
            sys.exit(-1)
        }
    }
  }
}
