package org.webrobot.cli.commands

import java.io.{IOException, InputStream}
import java.net.{HttpURLConnection, URL}

import com.google.gson.Gson
import eu.webrobot.openapi.client.{ApiClient, ApiException}
import org.apache.commons.io.IOUtils
import org.webrobot.cli.RunWebRobotCli
import org.webrobot.cli.commands.models.ErrorException
import org.webrobot.cli.utils.DataGrid
import WebRobot.Cli.Sdk.openapi.OpenApiSdkAdapter

class BaseSubCommand extends Runnable {

  protected var openApi: OpenApiSdkAdapter = _
  protected var dataGrid: DataGrid = _
  protected var errorManager: ErrorManager = new ErrorManager()

  val ANSI_RESET = "\u001B[0m"
  val ANSI_RED = "\u001B[31m"
  val ANSI_GREEN = "\u001B[32m"

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
