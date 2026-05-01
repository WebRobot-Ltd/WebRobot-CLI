package org.webrobot.cli.commands

import java.io.{File, FileWriter, PrintWriter}

import com.typesafe.config.ConfigFactory
import picocli.CommandLine.{Command, Parameters}

// ── shared helpers ────────────────────────────────────────────────────────────

object ConfigHelper {

  val configFile: File = new File(System.getProperty("user.home"), ".webrobot/config.cfg")

  // user-facing alias -> HOCON field name
  val keyToField: Map[String, String] = Map(
    "apikey"         -> "apikey",
    "endpoint"       -> "api_endpoint",
    "llm.provider"   -> "llm_provider",
    "llm.anthropic"  -> "anthropic_api_key",
    "llm.openai"     -> "openai_api_key",
    "llm.groq"       -> "groq_api_key",
    "llm.togetherai" -> "togetherai_api_key"
  )

  val fieldOrder: Seq[String] = Seq(
    "apikey", "api_endpoint", "llm_provider",
    "anthropic_api_key", "openai_api_key", "groq_api_key", "togetherai_api_key"
  )

  def readValues(): Map[String, String] = {
    if (!configFile.exists()) return Map.empty
    try {
      val cfg = ConfigFactory.parseFile(configFile).getConfig("webrobot.api.gateway.credentials")
      fieldOrder.flatMap { k =>
        try { Some(k -> cfg.getString(k)) } catch { case _: Exception => None }
      }.toMap
    } catch { case _: Exception => Map.empty }
  }

  def writeValues(values: Map[String, String]): Unit = {
    configFile.getParentFile.mkdirs()
    val sb = new StringBuilder
    sb.append("webrobot.api.gateway {\n")
    sb.append("  credentials {\n")
    for (k <- fieldOrder) {
      val v = values.getOrElse(k, "")
      if (v.nonEmpty) sb.append("    " + k + " = \"" + v + "\"\n")
    }
    sb.append("  }\n")
    sb.append("}\n")
    val pw = new PrintWriter(new FileWriter(configFile))
    try { pw.print(sb.toString()) } finally { pw.close() }
  }

  def maskValue(v: String): String = {
    if (v.isEmpty) "(not set)"
    else if (v.length <= 8) "****"
    else v.take(4) + "****" + v.takeRight(4)
  }
}

// ── config show ───────────────────────────────────────────────────────────────

@Command(
  name = "show",
  sortOptions = false,
  description = Array("Mostra la configurazione corrente (~/.webrobot/config.cfg) con chiavi mascherate.")
)
class RunConfigShowCommand extends Runnable {

  private val ANSI_CYAN  = "[36m"
  private val ANSI_RESET = "[0m"
  private val ANSI_BOLD  = "[1m"

  override def run(): Unit = {
    val values = ConfigHelper.readValues()
    if (values.isEmpty) {
      System.out.println("Nessuna configurazione trovata. Usa: webrobot config init")
      return
    }
    System.out.println(ANSI_BOLD + "Configurazione: " + ConfigHelper.configFile.getAbsolutePath + ANSI_RESET)
    System.out.println()

    val rows: Seq[(String, String, String)] = Seq(
      ("apikey",            ConfigHelper.keyToField("apikey"),         "WebRobot API key"),
      ("endpoint",          ConfigHelper.keyToField("endpoint"),       "API endpoint"),
      ("llm.provider",      ConfigHelper.keyToField("llm.provider"),   "Provider LLM attivo"),
      ("llm.anthropic",     ConfigHelper.keyToField("llm.anthropic"),  "Anthropic API key"),
      ("llm.openai",        ConfigHelper.keyToField("llm.openai"),     "OpenAI API key"),
      ("llm.groq",          ConfigHelper.keyToField("llm.groq"),       "Groq API key"),
      ("llm.togetherai",    ConfigHelper.keyToField("llm.togetherai"), "TogetherAI API key")
    )

    for ((alias, field, label) <- rows) {
      val raw = values.getOrElse(field, "")
      val masked = if (field == "api_endpoint" || field == "llm_provider") {
        if (raw.isEmpty) "(not set)" else raw
      } else {
        ConfigHelper.maskValue(raw)
      }
      System.out.println(
        "  " + ANSI_CYAN + "%-16s".format(alias) + ANSI_RESET + "  " + "%-20s".format(masked) + "  " + label
      )
    }
    System.out.println()
    System.out.println("Usa: webrobot config set <chiave> <valore>")
  }
}

// ── config set ────────────────────────────────────────────────────────────────

@Command(
  name = "set",
  sortOptions = false,
  description = Array(
    "Imposta un valore in ~/.webrobot/config.cfg.",
    "Chiavi valide: apikey | endpoint | llm.provider | llm.anthropic | llm.openai | llm.groq | llm.togetherai",
    "Esempio: webrobot config set llm.anthropic sk-ant-api03-..."
  )
)
class RunConfigSetCommand extends Runnable {

  @Parameters(index = "0", paramLabel = "<chiave>", description = Array("Chiave da impostare."))
  private var key: String = ""

  @Parameters(index = "1", paramLabel = "<valore>", description = Array("Nuovo valore."))
  private var value: String = ""

  override def run(): Unit = {
    val field = ConfigHelper.keyToField.get(key).orNull
    if (field == null) {
      System.err.println("Chiave non riconosciuta: " + key)
      System.err.println("Chiavi valide: " + ConfigHelper.keyToField.keys.toSeq.sorted.mkString(", "))
      sys.exit(1)
    }
    val current = ConfigHelper.readValues()
    val updated = current + (field -> value.trim)
    ConfigHelper.writeValues(updated)
    val display = if (field == "api_endpoint" || field == "llm_provider") value.trim
                  else ConfigHelper.maskValue(value.trim)
    System.out.println("OK: " + key + " = " + display)
    System.out.println("Config: " + ConfigHelper.configFile.getAbsolutePath)
  }
}

// ── config init ───────────────────────────────────────────────────────────────

@Command(
  name = "init",
  sortOptions = false,
  description = Array("Setup interattivo: guida alla configurazione di ~/.webrobot/config.cfg.")
)
class RunConfigInitCommand extends Runnable {

  private val ANSI_CYAN  = "[36m"
  private val ANSI_BOLD  = "[1m"
  private val ANSI_RESET = "[0m"

  override def run(): Unit = {
    System.out.println(ANSI_BOLD + "=== Configurazione WebRobot CLI ===" + ANSI_RESET)
    System.out.println("Premi Invio per mantenere il valore attuale (mostrato tra parentesi).")
    System.out.println()

    val current = ConfigHelper.readValues()

    def prompt(label: String, field: String, default: String, mask: Boolean): String = {
      val cur = current.getOrElse(field, "")
      val shown = if (mask) ConfigHelper.maskValue(cur) else if (cur.nonEmpty) cur else default
      System.out.print(ANSI_CYAN + label + ANSI_RESET + " [" + shown + "]: ")
      System.out.flush()
      val input = scala.io.StdIn.readLine()
      if (input == null || input.trim.isEmpty) cur else input.trim
    }

    val apikey      = prompt("WebRobot API key (wr-...)",         "apikey",            "",                          mask = true)
    val endpoint    = prompt("API endpoint",                       "api_endpoint",      "https://api.webrobot.eu",   mask = false)
    val provider    = prompt("Provider LLM (anthropic/openai/groq/togetherai)", "llm_provider", "anthropic", mask = false)
    val anthropic   = prompt("Anthropic API key  (console.anthropic.com)", "anthropic_api_key", "", mask = true)
    val openai      = prompt("OpenAI API key     (platform.openai.com)",   "openai_api_key",    "", mask = true)
    val groq        = prompt("Groq API key       (console.groq.com)",       "groq_api_key",      "", mask = true)
    val togetherai  = prompt("TogetherAI API key (api.together.ai)",        "togetherai_api_key","", mask = true)

    val merged = Map(
      "apikey"             -> apikey,
      "api_endpoint"       -> endpoint,
      "llm_provider"       -> provider,
      "anthropic_api_key"  -> anthropic,
      "openai_api_key"     -> openai,
      "groq_api_key"       -> groq,
      "togetherai_api_key" -> togetherai
    ).filter(_._2.nonEmpty)

    ConfigHelper.writeValues(merged)
    System.out.println()
    System.out.println("Configurazione salvata: " + ConfigHelper.configFile.getAbsolutePath)
  }
}

// ── root config command ───────────────────────────────────────────────────────

@Command(
  name = "config",
  mixinStandardHelpOptions = true,
  sortOptions = false,
  description = Array("Gestisce ~/.webrobot/config.cfg: API key WebRobot e chiavi LLM provider."),
  subcommands = Array(
    classOf[RunConfigShowCommand],
    classOf[RunConfigSetCommand],
    classOf[RunConfigInitCommand]
  )
)
class RunConfigCommand extends Runnable {

  override def run(): Unit = {
    System.err.println("Uso: webrobot config <sottocomando>")
    System.err.println("Sottocomandi: show | set | init")
  }
}
