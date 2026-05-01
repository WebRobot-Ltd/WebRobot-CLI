package org.webrobot.cli.commands

import picocli.CommandLine.Command

// ── status ────────────────────────────────────────────────────────────────────

@Command(
  name = "status",
  sortOptions = false,
  description = Array("Verifica lo stato dell'installazione di browser-use e camoufox.")
)
class RunBrowserStatusCommand extends Runnable {

  private val ANSI_GREEN  = "[32m"
  private val ANSI_RED    = "[31m"
  private val ANSI_YELLOW = "[33m"
  private val ANSI_CYAN   = "[36m"
  private val ANSI_BOLD   = "[1m"
  private val ANSI_RESET  = "[0m"

  override def run(): Unit = {
    System.out.println(ANSI_BOLD + "Browser-Use / Camoufox — stato installazione" + ANSI_RESET)
    System.out.println()

    checkCmd("python3",             "python3 --version")
    checkPkg("browser-use",        "browser-use")
    checkPkg("camoufox",           "camoufox")
    checkPkg("langchain-anthropic","langchain-anthropic")
    checkPkg("langchain-openai",   "langchain-openai")
    checkPkg("langchain-groq",     "langchain-groq")
    checkBinary()

    System.out.println()
    System.out.println("Per installare/aggiornare: " + ANSI_CYAN + "bash <(curl -fsSL <URL>/install-webrobot-cli.sh)" + ANSI_RESET)
  }

  private def run1(cmd: Seq[String], timeoutSecs: Int = 10): (Int, String) = try {
    val proc = new ProcessBuilder(cmd: _*).redirectErrorStream(true).start()
    val out  = new String(proc.getInputStream.readAllBytes()).trim
    proc.waitFor(timeoutSecs, java.util.concurrent.TimeUnit.SECONDS)
    (proc.exitValue(), out)
  } catch { case _: Exception => (-1, "") }

  private def checkCmd(label: String, cmd: String): Unit = {
    val parts = cmd.split(" ").toSeq
    val (code, out) = run1(parts)
    val ok   = code == 0
    val icon = if (ok) ANSI_GREEN + "✓" + ANSI_RESET else ANSI_RED + "✗" + ANSI_RESET
    val info = if (ok && out.nonEmpty) "  " + out.take(50) else "  (non trovato)"
    System.out.println(s"  $icon  $label$info")
  }

  private def checkPkg(label: String, pipName: String): Unit = {
    val script = "import importlib.metadata; print(importlib.metadata.version('" + pipName + "'))"
    val (code, out) = run1(Seq("python3", "-c", script))
    val ok   = code == 0 && out.nonEmpty && !out.startsWith("Traceback")
    val icon = if (ok) ANSI_GREEN + "✓" + ANSI_RESET else ANSI_RED + "✗" + ANSI_RESET
    val info = if (ok) "  v" + out.take(20) else "  (non installato)"
    System.out.println(s"  $icon  $label$info")
  }

  private def checkBinary(): Unit = {
    val script =
      "from camoufox.pkgman import camoufox_path; import os\n" +
      "p = camoufox_path()\n" +
      "print('ok' if os.path.exists(p) else 'fetch')\n"
    val (code, out) = run1(Seq("python3", "-c", script))
    val ok   = code == 0 && out.trim == "ok"
    val icon = if (ok) ANSI_GREEN + "✓" + ANSI_RESET else ANSI_YELLOW + "⚠" + ANSI_RESET
    val info = if (ok) "  binario pronto"
               else    "  binario mancante — riesegui lo script di installazione"
    System.out.println(s"  $icon  camoufox-binary$info")
  }
}

// ── root browser command ──────────────────────────────────────────────────────

@Command(
  name = "browser",
  mixinStandardHelpOptions = true,
  sortOptions = false,
  description = Array(
    "Verifica l'ambiente browser locale per il wizard probe (browser-use + camoufox).",
    "Installazione: bash <(curl -fsSL <URL>/install-webrobot-cli.sh)"
  ),
  subcommands = Array(
    classOf[RunBrowserStatusCommand]
  )
)
class RunBrowserCommand extends Runnable {
  override def run(): Unit = {
    System.err.println("Uso: webrobot browser <sottocomando>")
    System.err.println("Sottocomandi: status")
  }
}
