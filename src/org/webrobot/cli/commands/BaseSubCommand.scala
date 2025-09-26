package org.webrobot.cli.commands
import java.io.{IOException, InputStream}
import java.net.{HttpURLConnection, URL}
import java.nio.charset.StandardCharsets
import WebRobot.Cli.Sdk.WebRobotCliSdk
import com.amazonaws.opensdk.config.{ConnectionConfiguration, TimeoutConfiguration}
import com.google.common.hash.Hashing
import org.webrobot.cli.RunWebRobotCli
import org.webrobot.cli.utils.DataGrid
import java.util.Base64
import WebRobot.Cli.Sdk.Utils.Sha512Crypt
import com.amazonaws.opensdk.SdkRequestConfig
import com.google.gson.Gson
import org.apache.commons.io.IOUtils
import org.webrobot.cli.commands.models.ErrorException
class BaseSubCommand extends Runnable {
  protected var sdkClient : WebRobotCliSdk = null
  protected var dataGrid : DataGrid = null
  protected var errorManager: ErrorManager = new ErrorManager();
  val ANSI_RESET = "\u001B[0m"
  val ANSI_BLACK = "\u001B[30m"
  val ANSI_RED = "\u001B[31m"
  val ANSI_GREEN = "\u001B[32m"
  val ANSI_YELLOW = "\u001B[33m"
  val ANSI_BLUE = "\u001B[34m"
  val ANSI_PURPLE = "\u001B[35m"
  val ANSI_CYAN = "\u001B[36m"
  val ANSI_WHITE = "\u001B[37m"
  protected def uploadFile(url: URL, stream: InputStream): Unit = { // Create the connection and use it to upload the new object using the pre-signed URL.
    val connection = url.openConnection.asInstanceOf[HttpURLConnection]
    connection.setDoOutput(true)
    connection.setRequestMethod("PUT")
    IOUtils.copy(stream, connection.getOutputStream)
    // Check the HTTP response code. To complete the upload and make the object available,
    // you must interact with the connection object in some way.
    System.out.println("HTTP response code: " + connection.getResponseCode)
    System.out.println("HTTP response message: " + connection.getResponseMessage)
  }

  protected def getCustomSdkRequestConfig() : SdkRequestConfig =
  {
    val headerApiKey = this.generateApiKeyHeader()
    val headerAuthHeader = this.generateAuthHeader()

    val builder = SdkRequestConfig.builder()

    if (headerApiKey != null && !headerApiKey.isEmpty) {
      // Primary auth via X-API-Key
      builder.customHeader("X-API-Key", headerApiKey)
      // Compatibility auth via Authorization: ApiKey <key>
      builder.customHeader("Authorization", s"ApiKey ${headerApiKey}")
    }

    if (headerAuthHeader != null && !headerAuthHeader.isEmpty) {
      // Bearer or other scheme if provided
      builder.customHeader("Authorization", headerAuthHeader)
    }

    builder.build()
  }

  protected  def generateApiKeyHeader() : String =
  {
    var apiKey =  RunWebRobotCli.config.getString("apikey");
    return apiKey
  }

  protected def generateAuthHeader(): String =
  {
    // Prefer explicit JWT/Bearer token if present
    try {
      if (RunWebRobotCli.config.hasPath("jwt")) {
        val jwt = RunWebRobotCli.config.getString("jwt")
        if (jwt != null && !jwt.isEmpty) return s"Bearer ${jwt}"
      }
      if (RunWebRobotCli.config.hasPath("bearer") ) {
        val bearer = RunWebRobotCli.config.getString("bearer")
        if (bearer != null && !bearer.isEmpty) return s"Bearer ${bearer}"
      }
      if (RunWebRobotCli.config.hasPath("token") ) {
        val token = RunWebRobotCli.config.getString("token")
        if (token != null && !token.isEmpty) return s"Bearer ${token}"
      }
    } catch { case _: Throwable => () }

    // Fallback to ApiKey scheme if only apikey is configured
    try {
      val apiKey = RunWebRobotCli.config.getString("apikey")
      if (apiKey != null && !apiKey.isEmpty) return s"ApiKey ${apiKey}"
    } catch { case _: Throwable => () }

    ""
  }

  protected  def init() : Unit =
  {
    sdkClient = WebRobotCliSdk.builder()
      .connectionConfiguration(
        new ConnectionConfiguration()
          .maxConnections(100)
          .connectionMaxIdleMillis(1000))
      .timeoutConfiguration(
        new TimeoutConfiguration()
          .httpRequestTimeout(29000)
          .totalExecutionTimeout(29000)
          .socketTimeout(29000))
      .build();
    dataGrid = new DataGrid()
  }

  def startRun() : Unit =
  {

  }

  def run(): Unit =
  {
    try {
        this.startRun()
      System.out.println(ANSI_GREEN +  "OK: the command was successful"  + ANSI_RESET)
      }
    catch
      {
        case e: IOException =>
          {
            System.out.println(ANSI_RED + "KO: the file is not present in the system" + ANSI_RESET)
            sys.exit(-1)
          }
        case e: Exception =>
          {
            var message = e.getMessage
            try {

              var gson = new Gson();
              var errorException = gson.fromJson(message, classOf[ErrorException])
              if (errorException.getException == null)
                errorException.setException("")
              var errorMessage = errorManager.run(errorException)
              if (!errorMessage.equals(""))
                System.out.println(ANSI_RED + "KO:" + errorMessage + ANSI_RESET)
              else
                System.out.println(e.getMessage)
              sys.exit(-1)
            }
            catch
            {
              case e: Exception =>
              {
                System.out.println(ANSI_RED + "KO: generic exception with message:" + message + ANSI_RESET)
                sys.exit(-1)
              }
            }
          }
      }
  }
}
