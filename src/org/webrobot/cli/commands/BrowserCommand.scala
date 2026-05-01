package org.webrobot.cli.commands

import picocli.CommandLine.Command

// ── install ───────────────────────────────────────────────────────────────────

@Command(
  name = "install",
  sortOptions = false,
  description = Array(
    "Installa browser-use, camoufox e i provider LLM Python necessari per il wizard probe.",
    "Richiede python3 sul sistema. Scarica anche il binario camoufox (~100 MB).",
    "Eseguire una sola volta; poi il wizard probe usa automaticamente camoufox."
  )
)
class RunBrowserInstallCommand extends Runnable {

  private val ANSI_GREEN  = "[32m"
  private val ANSI_RED    = "[31m"
  private val ANSI_YELLOW = "[33m"
  private val ANSI_CYAN   = "[36m"
  private val ANSI_BOLD   = "[1m"
  private val ANSI_RESET  = "[0m"

  override def run(): Unit = {
    System.out.println(ANSI_BOLD + "=== Installazione Browser-Use + Camoufox ===" + ANSI_RESET)
    System.out.println()

    if (!checkPython()) {
      System.err.println(ANSI_RED + "✗ python3 non trovato. Installa Python 3.9+ e riprova." + ANSI_RESET)
      sys.exit(1)
    }
    System.out.println(ANSI_GREEN + "✓ python3 trovato" + ANSI_RESET)
    System.out.println()

    // Step 1: pip install
    System.out.println(ANSI_CYAN + "▶ [1/2] Installazione pacchetti pip..." + ANSI_RESET)
    val packages = java.util.Arrays.asList(
      "python3", "-m", "pip", "install", "--upgrade",
      "browser-use",
      "camoufox[geoip]",
      "langchain-anthropic",
      "langchain-openai",
      "langchain-groq"
    )
    val pipPb = new ProcessBuilder(packages)
    pipPb.inheritIO()
    val pipExit = pipPb.start().waitFor()
    System.out.println()

    if (pipExit != 0) {
      System.err.println(ANSI_RED + "✗ Installazione pip fallita (exit " + pipExit + ")." + ANSI_RESET)
      sys.exit(pipExit)
    }
    System.out.println(ANSI_GREEN + "✓ Pacchetti installati" + ANSI_RESET)
    System.out.println()

    // Step 2: fetch camoufox browser binary
    System.out.println(ANSI_CYAN + "▶ [2/2] Download binario camoufox (Firefox anti-detection, ~100 MB)..." + ANSI_RESET)
    val fetchPb = new ProcessBuilder("python3", "-m", "camoufox", "fetch")
    fetchPb.inheritIO()
    val fetchExit = fetchPb.start().waitFor()
    System.out.println()

    if (fetchExit != 0) {
      System.err.println(ANSI_YELLOW + "⚠ Download binario camoufox fallito (exit " + fetchExit + ")." + ANSI_RESET)
      System.err.println(ANSI_YELLOW + "  Il probe userà il browser Playwright di default (Chromium)." + ANSI_RESET)
      System.err.println(ANSI_YELLOW + "  Riprova manualmente: python3 -m camoufox fetch" + ANSI_RESET)
    } else {
      System.out.println(ANSI_GREEN + "✓ Binario camoufox scaricato" + ANSI_RESET)
    }

    System.out.println()
    System.out.println(ANSI_BOLD + ANSI_GREEN + "Installazione completata." + ANSI_RESET)
    System.out.println("Il wizard probe ora si aggancia automaticamente a camoufox (WebSocket locale).")
    System.out.println("Verifica: " + ANSI_CYAN + "webrobot browser status" + ANSI_RESET)
  }

  private def checkPython(): Boolean = try {
    val proc = new ProcessBuilder("python3", "--version").redirectErrorStream(true).start()
    proc.waitFor(10, java.util.concurrent.TimeUnit.SECONDS)
    proc.exitValue() == 0
  } catch { case _: Exception => false }
}

// ── status ────────────────────────────────────────────────────────────────────

@Command(
  name = "status",
  sortOptions = false,
  description = Array("Verifica lo stato dell'installazione di browser-use e camoufox.")
)
class RunBrowserStatusCommand extends Runnable {

  private val ANSI_GREEN  = "[32m"
  private val ANSI_RED    = "[31m"
  private val ANSI_YELLOW = "[33m"
  private val ANSI_CYAN   = "[36m"
  private val ANSI_BOLD   = "[1m"
  private val ANSI_RESET  = "[0m"

  override def run(): Unit = {
    System.out.println(ANSI_BOLD + "Browser-Use / Camoufox — stato installazione" + ANSI_RESET)
    System.out.println()

    checkCmd(  "python3",            "python3 --version")
    checkPkg(  "browser-use",        "browser_use",        "browser_use.__version__")
    checkPkg(  "camoufox",           "camoufox",           "camoufox.__version__")
    checkPkg(  "langchain-anthropic","langchain_anthropic", "langchain_anthropic.__version__")
    checkPkg(  "langchain-openai",   "langchain_openai",   "langchain_openai.__version__")
    checkPkg(  "langchain-groq",     "langchain_groq",     "langchain_groq.__version__")
    checkBinary()

    System.out.println()
    System.out.println("Per installare tutto: " + ANSI_CYAN + "webrobot browser install" + ANSI_RESET)
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

  private def checkPkg(label: String, module: String, versionExpr: String): Unit = {
    val script = "import " + module + "; print(" + versionExpr + ")"
    val (code, out) = run1(Seq("python3", "-c", script))
    val ok   = code == 0 && out.nonEmpty && !out.startsWith("Traceback")
    val icon = if (ok) ANSI_GREEN + "✓" + ANSI_RESET else ANSI_RED + "✗" + ANSI_RESET
    val info = if (ok) "  v" + out.take(20) else "  (non installato — webrobot browser install)"
    System.out.println(s"  $icon  $label$info")
  }

  private def checkBinary(): Unit = {
    // Check if camoufox binary is fetched by attempting a dry import of its path resolver
    val script =
      "import camoufox, os\n" +
      "try:\n" +
      "    from camoufox._utils import get_path\n" +
      "    p = get_path('camoufox')\n" +
      "    print('ok' if os.path.exists(p) else 'fetch')\n" +
      "except Exception:\n" +
      "    print('fetch')\n"
    val (code, out) = run1(Seq("python3", "-c", script))
    val ok   = code == 0 && out.trim == "ok"
    val icon = if (ok) ANSI_GREEN + "✓" + ANSI_RESET else ANSI_YELLOW + "⚠" + ANSI_RESET
    val info = if (ok) "  binario pronto (WebSocket locale attivo alla prima esecuzione)"
               else    "  binario mancante — esegui: python3 -m camoufox fetch"
    System.out.println(s"  $icon  camoufox-binary$info")
  }
}

// ── root browser command ──────────────────────────────────────────────────────

@Command(
  name = "browser",
  mixinStandardHelpOptions = true,
  sortOptions = false,
  description = Array(
    "Gestisce l'ambiente browser locale per il wizard probe.",
    "browser-use si aggancia a camoufox (Firefox anti-detection) via WebSocket locale.",
    "Prima esecuzione: webrobot browser install"
  ),
  subcommands = Array(
    classOf[RunBrowserInstallCommand],
    classOf[RunBrowserStatusCommand]
  )
)
class RunBrowserCommand extends Runnable {
  override def run(): Unit = {
    System.err.println("Uso: webrobot browser <sottocomando>")
    System.err.println("Sottocomandi: install | status")
  }
}
