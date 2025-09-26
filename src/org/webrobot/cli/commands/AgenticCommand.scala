package org.webrobot.cli.commands
import picocli.CommandLine.{Command, Option}

@Command(name = "agentic", sortOptions = false,
  description = Array(
    "Interact with the agentic backend (start/resume/chat)"),
  footer = Array(),
  subcommands = Array(classOf[AgenticStartCommand], classOf[AgenticResumeCommand], classOf[AgenticChatCommand])
)
class AgenticCommand extends Runnable  {
  def run(): Unit = {
    System.out.println("agentic: start|resume|chat")
  }
}

@Command(name = "start", sortOptions = false,
  description = Array(
    "Start an agentic conversation; returns execution_id if human input is required"),
  footer = Array()
)
class AgenticStartCommand extends BaseSubCommand {

  @Option(names = Array("-m", "--message"), description = Array("initial user message"), required = true)
  private var message : String = ""

  @Option(names = Array("-s", "--sessionId"), description = Array("session id"), required = true)
  private var sessionId : String = ""

  @Option(names = Array("-u", "--userId"), description = Array("user id"), required = false)
  private var userId : String = ""

  override def startRun(): Unit =
  {
    val base = this.getAgenticBaseUrl()
    val url = s"${base}/agentic/start"
    val payload = s"""{
      \"message\": ${escapeJsonString(message)},
      \"sessionId\": ${escapeJsonString(sessionId)},
      \"userId\": ${escapeJsonString(userId)}
    }"""
    val response = this.postJson(url, payload)
    System.out.println(response)
  }

  private def escapeJsonString(value: String): String = {
    val v = if (value == null) "" else value
    "\"" + v.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
  }
}

@Command(name = "resume", sortOptions = false,
  description = Array(
    "Resume a paused execution by providing human input"),
  footer = Array()
)
class AgenticResumeCommand extends BaseSubCommand {

  @Option(names = Array("-e", "--executionId"), description = Array("execution id"), required = true)
  private var executionId : String = ""

  @Option(names = Array("-i", "--input"), description = Array("human input"), required = true)
  private var userInput : String = ""

  override def startRun(): Unit =
  {
    val base = this.getAgenticBaseUrl()
    val url = s"${base}/agentic/resume"
    val payload = s"""{
      \"execution_id\": ${escapeJsonString(executionId)},
      \"user_input\": ${escapeJsonString(userInput)}
    }"""
    val response = this.postJson(url, payload)
    System.out.println(response)
  }

  private def escapeJsonString(value: String): String = {
    val v = if (value == null) "" else value
    "\"" + v.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
  }
}

@Command(name = "chat", sortOptions = false,
  description = Array(
    "Interactive chat with the agentic backend (Claude/Cursor style)"),
  footer = Array()
)
class AgenticChatCommand extends BaseSubCommand {

  @Option(names = Array("-s", "--sessionId"), description = Array("session id"), required = false)
  private var sessionId : String = ""

  @Option(names = Array("-u", "--userId"), description = Array("user id"), required = false)
  private var userId : String = ""

  override def startRun(): Unit =
  {
    val base = this.getAgenticBaseUrl()
    var execId : String = null
    var sid = if (sessionId != null && !sessionId.isEmpty) sessionId else java.util.UUID.randomUUID().toString

    System.out.println(s"Session: ${sid}. Type /exit to quit.")
    var continue = true
    while (continue) {
      System.out.print("You> ")
      val line = scala.io.StdIn.readLine()
      if (line == null) return
      if (line.trim.equalsIgnoreCase("/exit")) {
        continue = false
      } else if (execId == null || execId.isEmpty) {
        val url = s"${base}/agentic/start"
        val payload = s"""{\"message\": ${escapeJsonString(line)}, \"sessionId\": ${escapeJsonString(sid)}, \"userId\": ${escapeJsonString(userId)} }"""
        val response = this.postJson(url, payload)
        System.out.println(s"Agentic< ${response}")
        // Try to extract execution_id to continue if needed
        execId = extractExecutionId(response)
      } else {
        val url = s"${base}/agentic/resume"
        val payload = s"""{\"execution_id\": ${escapeJsonString(execId)}, \"user_input\": ${escapeJsonString(line)} }"""
        val response = this.postJson(url, payload)
        System.out.println(s"Agentic< ${response}")
        // Clear execId after resume
        execId = null
      }
    }
  }

  private def escapeJsonString(value: String): String = {
    val v = if (value == null) "" else value
    "\"" + v.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
  }

  private def extractExecutionId(json: String): String = {
    if (json == null) return null
    val key = "\"execution_id\":\""
    val idx = json.indexOf(key)
    if (idx >= 0) {
      val start = idx + key.length
      val end = json.indexOf("\"", start)
      if (end > start) json.substring(start, end) else null
    } else null
  }
}


