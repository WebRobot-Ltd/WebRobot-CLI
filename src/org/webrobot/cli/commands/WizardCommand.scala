package org.webrobot.cli.commands

import eu.webrobot.openapi.client.model.{AgentDto, JobDto}
import org.webrobot.cli.manifest.{AtEnd, StageCatalog, YamlManifest}
import org.webrobot.cli.openapi.{JsonCliUtil, OpenApiHttp}
import picocli.CommandLine.{Command, Option => Opt}

import java.io.File
import scala.collection.JavaConverters._
import scala.collection.mutable.ListBuffer

// ─── IO helpers ───────────────────────────────────────────────────────────────

private object WizardIO {
  def prompt(label: String, default: String = "")(implicit cmd: BaseSubCommand): String = {
    val hint = if (default.nonEmpty) s" [${cmd.ANSI_CYAN}$default${cmd.ANSI_RESET}]" else ""
    System.out.print(s"  $label$hint: ")
    System.out.flush()
    val line = scala.io.StdIn.readLine()
    if (line == null || line.trim.isEmpty) default else line.trim
  }

  def confirm(msg: String)(implicit cmd: BaseSubCommand): Boolean = {
    System.out.print(s"  $msg [Y/n]: ")
    System.out.flush()
    val line = scala.io.StdIn.readLine()
    line == null || line.trim.isEmpty || line.trim.equalsIgnoreCase("y")
  }

  def header(title: String)(implicit cmd: BaseSubCommand): Unit = {
    System.out.println(s"\n${cmd.ANSI_BOLD}$title${cmd.ANSI_RESET}")
    System.out.println("  " + "─" * (title.length + 2))
  }

  def showCommand(cmd2: BaseSubCommand, equivalent: String): Unit = {
    System.out.println(s"\n  ${cmd2.ANSI_BOLD}Equivalent command:${cmd2.ANSI_RESET}")
    System.out.println(s"  ${cmd2.ANSI_CYAN}$equivalent${cmd2.ANSI_RESET}\n")
  }
}

// ─── shared stage composer ────────────────────────────────────────────────────

private object StageWizardHelper {

  def collectStageArgs(stageName: String, suggestedArgs: List[String] = Nil)(implicit cmd: BaseSubCommand): Option[(String, List[String])] = {
    import cmd._
    val base     = StageCatalog.resolveBase(stageName)
    val stageDef = StageCatalog.find(base)

    System.out.println()
    System.out.println(s"  ${ANSI_BOLD}Stage: ${ANSI_CYAN}$stageName${ANSI_RESET}")
    stageDef.foreach { s =>
      val desc = s.getOrElse("description", "").toString
      if (desc.nonEmpty) System.out.println(s"  $desc")
      s.get("example").foreach { ex =>
        System.out.println(s"  ${ANSI_BOLD}Example:${ANSI_RESET}")
        ex.toString.linesIterator.foreach(l => System.out.println(s"    $l"))
      }
    }
    System.out.println()

    val argDefs: List[java.util.Map[String, Any]] = stageDef.flatMap(_.get("args")).collect {
      case l: java.util.List[_] =>
        l.asInstanceOf[java.util.List[java.util.Map[String, Any]]].asScala.toList
    }.getOrElse(List.empty)

    val collected = ListBuffer[String]()

    if (argDefs.nonEmpty) {
      System.out.println(s"  ${ANSI_BOLD}Parameters:${ANSI_RESET}")
      argDefs.zipWithIndex.foreach { case (argDef, idx) =>
        val aName    = Option(argDef.get("name")).map(_.toString).getOrElse("")
        val aType    = Option(argDef.get("type")).map(_.toString).getOrElse("string")
        val aDesc    = Option(argDef.get("description")).map(_.toString).getOrElse("")
        val aReq     = Option(argDef.get("required")).exists(_.toString == "true")
        val catDef   = Option(argDef.get("default")).map(_.toString).getOrElse("")
        // LLM-suggested arg takes priority over catalog default
        val aDefault = if (idx < suggestedArgs.size && suggestedArgs(idx).nonEmpty) suggestedArgs(idx) else catDef
        val reqMark  = if (aReq) s" ${ANSI_RED}*${ANSI_RESET}" else s" ${ANSI_YELLOW}(optional)${ANSI_RESET}"
        System.out.println(s"    ${ANSI_CYAN}$aName${ANSI_RESET} [$aType]$reqMark — $aDesc")
        val value = WizardIO.prompt(s"    $aName", aDefault)
        collected += (if (value.nonEmpty || aReq) value else "")
      }
    } else if (base == "load_csv") {
      // load_csv has no catalog schema — always default to ${INPUT_CSV_PATH}
      val defaultPath = if (suggestedArgs.nonEmpty && suggestedArgs.head.nonEmpty) suggestedArgs.head else "${INPUT_CSV_PATH}"
      System.out.println(s"  Loads CSV dataset. Path is substituted at job run time.")
      val path = WizardIO.prompt("  CSV path", defaultPath)
      if (path.nonEmpty) collected += path
    } else if (isIextract(base)) {
      // iextract: extraction prompt must contain AS aliases for column projection
      System.out.println(s"  AI-powered field extraction.")
      System.out.println(s"  Format: <field description> as <column_name>, ...")
      val sugDefault = if (suggestedArgs.nonEmpty && suggestedArgs.head.nonEmpty) suggestedArgs.head else ""
      val raw = WizardIO.prompt("  Extraction prompt", sugDefault)
      if (raw.nonEmpty) {
        val normalized = normalizeIextractPrompt(raw)
        if (normalized != raw.trim)
          System.out.println(s"  ${ANSI_CYAN}Normalized: $normalized${ANSI_RESET}")
        collected += normalized
      }
    } else {
      System.out.println(s"  ${ANSI_YELLOW}No parameter schema available.${ANSI_RESET}")
      val defaultRaw = suggestedArgs.mkString(", ")
      val raw = WizardIO.prompt("  Args (comma-separated, leave empty for none)", defaultRaw)
      if (raw.nonEmpty) raw.split(",").map(_.trim).foreach(collected += _)
    }

    while (collected.nonEmpty && collected.last.isEmpty) collected.remove(collected.size - 1)

    val args = collected.toList
    System.out.println(s"\n  ${ANSI_BOLD}YAML snippet:${ANSI_RESET}")
    System.out.print(s"    ${ANSI_CYAN}- stage: $stageName${ANSI_RESET}")
    if (args.nonEmpty)
      System.out.print(s"\n    ${ANSI_CYAN}  args: [${args.map(a => if (a.matches("[0-9]+\\.?[0-9]*")) a else s""""$a"""").mkString(", ")}]${ANSI_RESET}")
    System.out.println("\n")

    if (!WizardIO.confirm(s"Add stage '$stageName'?")) {
      System.out.println(s"  ${ANSI_YELLOW}Stage skipped.${ANSI_RESET}"); None
    } else Some((stageName, args))
  }

  def pickFromList(items: List[Map[String, AnyRef]]): String = {
    System.out.print(s"  Choose (number or name, Enter to cancel): ")
    System.out.flush()
    val pick = Option(scala.io.StdIn.readLine()).map(_.trim).getOrElse("")
    if (pick.isEmpty) "" else try {
      val n = pick.toInt
      if (n >= 1 && n <= items.size) items(n - 1).getOrElse("name", "").toString else pick
    } catch { case _: NumberFormatException => pick }
  }

  def resolveStageInput(input: String)(implicit cmd: BaseSubCommand): String = {
    import cmd._
    if (StageCatalog.categories.exists(_.equalsIgnoreCase(input))) {
      val inCat = StageCatalog.list(category = Some(input))
      System.out.println(s"\n  ${ANSI_BOLD}Stages in '$input':${ANSI_RESET}")
      inCat.zipWithIndex.foreach { case (s, i) =>
        System.out.println(s"  [${i + 1}] ${ANSI_CYAN}${s.getOrElse("name", "")}${ANSI_RESET} — ${s.getOrElse("description", "").toString.take(55)}")
      }
      System.out.println()
      pickFromList(inCat)
    } else {
      val base    = StageCatalog.resolveBase(input)
      val matches = StageCatalog.list(search = Some(input))
      if (StageCatalog.exists(base)) base
      else if (matches.size == 1) {
        val n = matches.head.getOrElse("name", "").toString
        System.out.println(s"  ${ANSI_YELLOW}Found: $n${ANSI_RESET}"); n
      } else if (matches.size > 1) {
        System.out.println(s"\n  ${ANSI_BOLD}Results for '$input':${ANSI_RESET}")
        matches.take(8).zipWithIndex.foreach { case (s, i) =>
          System.out.println(s"  [${i + 1}] ${ANSI_CYAN}${s.getOrElse("name", "")}${ANSI_RESET} — ${s.getOrElse("description", "").toString.take(55)}")
        }
        pickFromList(matches)
      } else {
        System.out.println(s"  ${ANSI_YELLOW}Stage '$input' not found in catalog — adding anyway.${ANSI_RESET}")
        input
      }
    }
  }

  /**
   * Asks LLM to suggest a stage sequence from a natural-language description.
   * Returns a list of confirmed (name, args) pairs ready to use.
   */
  def suggestStagesFromDescription(description: String, probeContext: Option[String] = None)(implicit cmd: BaseSubCommand): List[(String, List[String])] = {
    import cmd._
    import scala.collection.JavaConverters._

    val allStages = StageCatalog.list()
    val mapper    = new com.fasterxml.jackson.databind.ObjectMapper()

    // Build catalog grouped: AI-powered stages flagged explicitly
    val aiCategories  = Set("intelligent")
    val navCategories = Set("crawling", "external-api")

    def stageCategory(s: Map[String, Any]): String = s.getOrElse("category", "").toString.toLowerCase
    def isAi(name: String, cat: String): Boolean    = aiCategories.contains(cat) ||
      Seq("iextract", "intelligent", "llm_", "_ai", "gpt").exists(name.contains)
    def isNav(name: String, cat: String): Boolean   = navCategories.contains(cat)

    val navStages  = allStages.filter(s => isNav(s.getOrElse("name","").toString, stageCategory(s)))
    val aiStages   = allStages.filter(s => isAi(s.getOrElse("name","").toString, stageCategory(s)) &&
                                           !isNav(s.getOrElse("name","").toString, stageCategory(s)))
    val detStages  = allStages.filterNot(s =>
      isAi(s.getOrElse("name","").toString, stageCategory(s)) ||
      isNav(s.getOrElse("name","").toString, stageCategory(s)))

    def fmtStage(s: Map[String, Any]): String = {
      val n = s.getOrElse("name", "").toString
      val d = s.getOrElse("description", "").toString.take(120)
      s"  $n — $d"
    }

    val catalogSummary =
      "=== INPUT DATASET STAGE (use as VERY FIRST stage when pipeline reads from a file or URL list) ===\n" +
      "  load_csv — Loads input CSV dataset; first arg is the file path; use \"${INPUT_CSV_PATH}\" for job-time injection\n" +
      "\n=== NAVIGATION STAGES (ALWAYS required after dataset load — never skip) ===\n" +
      navStages.map(fmtStage).mkString("\n") +
      "\n\n=== AI-POWERED STAGES (prefer for extraction/processing when content varies) ===\n" +
      (if (aiStages.isEmpty) "  (none)\n" else aiStages.map(fmtStage).mkString("\n")) +
      "\n\n=== DETERMINISTIC STAGES (use only when structure is guaranteed) ===\n" +
      detStages.map(fmtStage).mkString("\n")

    val systemPrompt =
      """You are a WebRobot ETL pipeline expert. Suggest the BEST stage sequence for a web scraping pipeline.

RULES (follow strictly):
1. If the pipeline reads data (URLs, records, etc.) from a file or list, ALWAYS include load_csv with "${INPUT_CSV_PATH}" as the VERY FIRST stage before any navigation stage.
2. ALWAYS include a NAVIGATION stage (visit, wget, join, etc.) to load the page or call the API — it is mandatory.
3. For EXTRACTION steps, ALWAYS prefer AI-powered stages (iextract, intelligentExtract, etc.) over deterministic ones when content may vary. Include the deterministic alternative so the user can choose.
4. For EACH stage, populate "args" with the actual parameter values the user should start from (e.g. "$url" for visit, "${INPUT_CSV_PATH}" for load_csv, "extract field as col" for iextract/extract).
5. Return ONLY a valid JSON array of objects — no markdown, no explanation outside JSON.

Response format:
[
  {
    "stage": "best_stage_name",
    "args": ["arg1_value", "arg2_value"],
    "reason": "one-line explanation",
    "alternatives": ["alt_stage"]
  }
]

"args" must contain realistic default values (never empty strings).
For iextract/intelligentExtract stages, "args" must be a single-element array with the extraction prompt already in "field desc as col_name, ..." format.
Set "alternatives" to [] when no alternative is relevant.
Use ONLY stage names from the catalog."""

    val probeSection = probeContext.map("\n\n---\n" + _ + "\n").getOrElse("")
    val userPrompt =
      "STAGE CATALOG:\n" + catalogSummary +
      probeSection +
      "\n\n---\nPIPELINE DESCRIPTION: " + description +
      "\n\nSuggest the full stage sequence. Start with navigation. Prefer AI-powered extraction. " +
      "If a BROWSER USE PAGE PROBE section is present, use the fields and suggested_extraction_prompt " +
      "from it to fill iextract args with real field names. Provide alternatives where applicable."

    System.out.println(s"  ${ANSI_CYAN}Calling LLM for stage suggestions...${ANSI_RESET}")
    val raw = cmd.llmInfer(userPrompt, systemPrompt).getOrElse("")
    if (raw.isEmpty) {
      System.out.println(s"  ${ANSI_YELLOW}No LLM suggestions available.${ANSI_RESET}")
      return List.empty
    }

    // Parse JSON array (robust: find first '[' ... ']')
    val start = raw.indexOf('[')
    val end   = raw.lastIndexOf(']')
    if (start < 0 || end < 0 || end <= start) {
      System.out.println(s"  ${ANSI_YELLOW}Unparseable LLM response: $raw${ANSI_RESET}")
      return List.empty
    }

    // Each element: { stage, args[], reason, alternatives[] }
    case class Suggestion(stage: String, args: List[String], reason: String, alternatives: List[String])
    val suggestions: List[Suggestion] = try {
      val node = mapper.readTree(raw.substring(start, end + 1))
      if (!node.isArray) List.empty
      else node.elements().asScala.flatMap { el =>
        val stage = el.path("stage").asText("").trim
        if (stage.isEmpty) None
        else Some(Suggestion(
          stage        = stage,
          args         = if (el.path("args").isArray)
            el.path("args").elements().asScala.map(_.asText("")).filter(_.nonEmpty).toList
          else List.empty,
          reason       = el.path("reason").asText(""),
          alternatives = if (el.path("alternatives").isArray)
            el.path("alternatives").elements().asScala.map(_.asText("")).filter(_.nonEmpty).toList
          else List.empty
        ))
      }.toList
    } catch { case _: Exception => List.empty }

    if (suggestions.isEmpty) {
      System.out.println(s"  ${ANSI_YELLOW}LLM returned no valid stages.${ANSI_RESET}")
      return List.empty
    }

    System.out.println(s"\n  ${ANSI_BOLD}LLM-suggested stages:${ANSI_RESET}\n")
    suggestions.zipWithIndex.foreach { case (s, i) =>
      val aiMark = if (isAi(s.stage, "")) s" ${ANSI_CYAN}[AI]${ANSI_RESET}" else ""
      System.out.println(s"  [${i + 1}] ${ANSI_BOLD}${s.stage}${ANSI_RESET}$aiMark")
      if (s.reason.nonEmpty)
        System.out.println(s"      ${ANSI_YELLOW}→ ${s.reason}${ANSI_RESET}")
      if (s.alternatives.nonEmpty)
        System.out.println(s"      Alternative: ${s.alternatives.map(a => s"${ANSI_CYAN}$a${ANSI_RESET}").mkString(", ")}")
      System.out.println()
    }

    val collected = new scala.collection.mutable.ListBuffer[(String, List[String])]()
    suggestions.foreach { sug =>
      val allOptions = sug.stage :: sug.alternatives
      if (allOptions.size == 1) {
        // No alternatives — simple Y/n
        System.out.print(s"  Include ${ANSI_CYAN}${sug.stage}${ANSI_RESET}? [Y/n]: ")
        System.out.flush()
        val ans = Option(scala.io.StdIn.readLine()).map(_.trim).getOrElse("")
        if (ans.isEmpty || ans.equalsIgnoreCase("y"))
          collectStageArgs(sug.stage, sug.args).foreach { e => collected += e; System.out.println(s"  ${ANSI_GREEN}✓ '${e._1}' added.${ANSI_RESET}\n") }
      } else {
        // Show choice between primary and alternatives
        System.out.println(s"  Stage for this step:")
        allOptions.zipWithIndex.foreach { case (o, i) =>
          val aiMark = if (isAi(o, "")) s" ${ANSI_CYAN}[AI]${ANSI_RESET}" else ""
          System.out.println(s"    [${i + 1}] ${ANSI_CYAN}$o${ANSI_RESET}$aiMark")
        }
        System.out.println(s"    [0] Skip")
        System.out.print(s"  Choice [1]: ")
        System.out.flush()
        val ans = Option(scala.io.StdIn.readLine()).map(_.trim).getOrElse("1")
        val idx = try ans.toInt catch { case _: Exception => 1 }
        if (idx >= 1 && idx <= allOptions.size) {
          val chosen = allOptions(idx - 1)
          // use suggested args only when the primary stage is chosen; alternatives get no pre-fill
          val chosenArgs = if (idx == 1) sug.args else Nil
          collectStageArgs(chosen, chosenArgs).foreach { e => collected += e; System.out.println(s"  ${ANSI_GREEN}✓ '${e._1}' added.${ANSI_RESET}\n") }
        }
      }
    }
    collected.toList
  }

  def stagesToYaml(stages: List[(String, List[String])]): String = {
    val sb = new StringBuilder("stages:\n")
    stages.foreach { case (name, args) =>
      sb.append(s"  - stage: $name\n")
      if (args.nonEmpty) {
        sb.append("    args:\n")
        args.foreach { a =>
          val v = if (a.matches("[0-9]+\\.?[0-9]*")) a else s""""$a""""
          sb.append(s"      - $v\n")
        }
      }
    }
    sb.toString
  }

  private def isIextract(base: String): Boolean = {
    val b = base.toLowerCase.replaceAll("[_\\-]", "")
    b == "iextract" || b == "intelligentextract" ||
      (b.startsWith("i") && b.endsWith("extract")) ||
      (b.contains("intelligent") && b.contains("extract"))
  }

  private def normalizeIextractPrompt(prompt: String)(implicit cmd: BaseSubCommand): String = {
    // Already has AS aliases — return as-is
    if (prompt.toLowerCase.contains(" as ")) return prompt.trim

    System.out.println(s"  ${cmd.ANSI_YELLOW}No 'as' aliases detected — normalizing with LLM...${cmd.ANSI_RESET}")
    val sysPrompt =
      "You normalize WebRobot iextract extraction prompts. " +
      "Add 'as <snake_case_column>' aliases to every field. " +
      "Return ONLY the normalized prompt string — no explanations, no quotes, no JSON."
    val userPrompt =
      "Add 'as <snake_case_column>' to each field in this iextract extraction prompt.\n" +
      "Rules:\n" +
      "- Every field needs exactly one 'as <column>' suffix\n" +
      "- Column names must be snake_case\n" +
      "- Keep original field description intact before 'as'\n" +
      "- Separate fields with ', '\n" +
      "Example input:  price, product full name, number of reviews\n" +
      "Example output: price as price, product full name as product_full_name, number of reviews as number_of_reviews\n" +
      "Input: " + prompt
    cmd.llmInfer(userPrompt, sysPrompt).map(_.trim).getOrElse(prompt.trim)
  }
}

// ─── browser-use probe ───────────────────────────────────────────────────────

private object BrowserUseProbe {

  private val PROBE_SCRIPT = "import asyncio, json, sys, os, re\n" +
    "try:\n" +
    "    from browser_use import Agent\n" +
    "except ImportError as e:\n" +
    "    print(json.dumps({'error': 'browser_use not installed: ' + str(e)}))\n" +
    "    sys.exit(0)\n" +
    "\n" +
    "async def probe(url, objective):\n" +
    "    llm = None\n" +
    "    try:\n" +
    "        if os.environ.get('ANTHROPIC_API_KEY'):\n" +
    "            from langchain_anthropic import ChatAnthropic\n" +
    "            llm = ChatAnthropic(model_name='claude-haiku-4-5-20251001', timeout=60, stop=None)\n" +
    "        elif os.environ.get('OPENAI_API_KEY'):\n" +
    "            from langchain_openai import ChatOpenAI\n" +
    "            llm = ChatOpenAI(model='gpt-4o-mini')\n" +
    "        elif os.environ.get('GROQ_API_KEY'):\n" +
    "            from langchain_groq import ChatGroq\n" +
    "            llm = ChatGroq(model='llama-3.3-70b-versatile')\n" +
    "        else:\n" +
    "            print(json.dumps({'error': 'No LLM key (set ANTHROPIC_API_KEY, OPENAI_API_KEY or GROQ_API_KEY)'}))\n" +
    "            return\n" +
    "    except ImportError as e:\n" +
    "        print(json.dumps({'error': 'LLM provider not installed: ' + str(e)}))\n" +
    "        return\n" +
    "    task = (\n" +
    "        'Navigate to ' + url + '.\\n'\n" +
    "        'Goal: ' + objective + '\\n\\n'\n" +
    "        'Explore the page and return ONLY this JSON object (no extra text):\\n'\n" +
    "        '{\"page_title\": \"...\",'\n" +
    "        ' \"fields\": [{\"name\": \"field\", \"description\": \"what it contains\", \"example_value\": \"...\", \"css_selector\": \"optional\"}],'\n" +
    "        ' \"actions_taken\": [\"list of actions\"],'\n" +
    "        ' \"suggested_extraction_prompt\": \"price as price, title as title, ...\"}'\n" +
    "    )\n" +
    "    try:\n" +
    "        agent = Agent(task=task, llm=llm)\n" +
    "        history = await agent.run(max_steps=12)\n" +
    "        last_msg = ''\n" +
    "        if hasattr(history, 'final_result') and callable(history.final_result):\n" +
    "            last_msg = history.final_result() or ''\n" +
    "        else:\n" +
    "            last_msg = str(history)\n" +
    "        m = re.search(r'\\{[\\s\\S]*\\}', last_msg)\n" +
    "        if m:\n" +
    "            try:\n" +
    "                print(json.dumps(json.loads(m.group())))\n" +
    "                return\n" +
    "            except Exception:\n" +
    "                pass\n" +
    "        print(json.dumps({'summary': last_msg[:3000], 'actions_taken': [], 'fields': []}))\n" +
    "    except Exception as e:\n" +
    "        print(json.dumps({'error': str(e)}))\n" +
    "\n" +
    "asyncio.run(probe(sys.argv[1], sys.argv[2] if len(sys.argv) > 2 else 'explore the page'))\n"

  def isAvailable: Boolean = try {
    val proc = new ProcessBuilder("python3", "-c", "import browser_use; print('ok')")
      .redirectErrorStream(true).start()
    val out  = new String(proc.getInputStream.readAllBytes()).trim
    proc.waitFor(10, java.util.concurrent.TimeUnit.SECONDS)
    out.contains("ok")
  } catch { case _: Exception => false }

  def run(url: String, objective: String, timeoutSecs: Int = 90)(implicit cmd: BaseSubCommand): Option[Map[String, Any]] = {
    import cmd._
    val scriptFile = java.io.File.createTempFile("wr_probe_", ".py")
    try {
      java.nio.file.Files.write(scriptFile.toPath, PROBE_SCRIPT.getBytes("UTF-8"))
      val pb = new ProcessBuilder("python3", scriptFile.getAbsolutePath, url, objective)
      pb.environment().putAll(System.getenv())
      val proc     = pb.start()
      val stdout   = new String(proc.getInputStream.readAllBytes(), "UTF-8").trim
      val stderr   = new String(proc.getErrorStream.readAllBytes(), "UTF-8").trim
      val finished = proc.waitFor(timeoutSecs, java.util.concurrent.TimeUnit.SECONDS)
      if (!finished) { proc.destroyForcibly(); System.out.println(s"  ${ANSI_YELLOW}Probe timed out after ${timeoutSecs}s.${ANSI_RESET}") }
      if (stderr.nonEmpty) System.out.println(s"  ${ANSI_YELLOW}[probe] ${stderr.take(300)}${ANSI_RESET}")
      if (stdout.isEmpty) return None
      val mapper = new com.fasterxml.jackson.databind.ObjectMapper()
      val node   = mapper.readTree(stdout)
      if (node.has("error")) {
        System.out.println(s"  ${ANSI_YELLOW}Probe: ${node.path("error").asText()}${ANSI_RESET}")
        return None
      }
      import scala.collection.JavaConverters._
      val m = scala.collection.mutable.Map[String, Any]()
      node.fields().asScala.foreach { e => m(e.getKey) = e.getValue.asText("") }
      Some(m.toMap)
    } catch {
      case e: Exception =>
        System.out.println(s"  ${ANSI_YELLOW}Probe error: ${e.getMessage}${ANSI_RESET}")
        None
    } finally { scriptFile.delete() }
  }

  def summarize(result: Map[String, Any]): String = {
    val mapper = new com.fasterxml.jackson.databind.ObjectMapper()
    val sb = new StringBuilder()
    sb.append("=== BROWSER USE PAGE PROBE ===\n")
    result.get("page_title").foreach(t => sb.append(s"Page title: $t\n"))
    result.get("actions_taken").foreach(a => sb.append(s"Actions taken: $a\n"))
    result.get("fields").foreach { f =>
      sb.append("Fields found on page:\n")
      try {
        val arr = mapper.readTree(f.toString)
        if (arr.isArray) arr.elements().asScala.foreach { el =>
          val name = el.path("name").asText("")
          val desc = el.path("description").asText("")
          val ex   = el.path("example_value").asText("")
          val css  = el.path("css_selector").asText("")
          sb.append(s"  - $name: $desc")
          if (ex.nonEmpty)  sb.append(s" (e.g. '$ex')")
          if (css.nonEmpty) sb.append(s" [css: $css]")
          sb.append("\n")
        }
      } catch { case _: Exception => sb.append(s"  $f\n") }
    }
    result.get("suggested_extraction_prompt").foreach { p =>
      val pStr = p.toString
      if (pStr.nonEmpty && pStr != "null")
        sb.append(s"Suggested iextract prompt: $pStr\n")
    }
    result.get("summary").foreach(s => sb.append(s"Summary: ${s.toString.take(500)}\n"))
    sb.toString
  }
}

// ─── dataset wizard trait ─────────────────────────────────────────────────────

trait DatasetWizard { self: BaseSubCommand =>

  private val colPattern = java.util.regex.Pattern.compile("\\$(?!\\{)([a-zA-Z_][a-zA-Z0-9_]*)")

  protected def extractColumns(text: String): List[String] = {
    val m = colPattern.matcher(text)
    val cols = scala.collection.mutable.LinkedHashSet[String]()
    while (m.find()) cols += m.group(1)
    cols.toList
  }

  protected def buildCsvInteractive(columns: List[String]): String = {
    implicit val _cmd: BaseSubCommand = self
    val sb = new StringBuilder(columns.mkString(",") + "\n")
    var rowNum = 1
    var adding = true
    while (adding) {
      System.out.println(s"  ${ANSI_BOLD}Row $rowNum${ANSI_RESET} — leave first field empty to finish:")
      val vals = columns.map(col => WizardIO.prompt(s"    $col"))
      if (vals.head.isEmpty) {
        adding = false
      } else {
        val escaped = vals.map(v =>
          if (v.contains(",") || v.contains("\"")) s""""${v.replace("\"", "\"\"")}"""" else v)
        sb.append(escaped.mkString(",") + "\n")
        rowNum += 1
      }
    }
    sb.toString
  }

  // Selects a category → agent → fetches agent code. Returns (categoryId, agentId, agentCode).
  protected def selectAgentWithCode(): (String, String, String) = {
    implicit val _cmd: BaseSubCommand = this
    System.out.println(s"  ${ANSI_CYAN}Loading categories...${ANSI_RESET}")
    val catsNode = OpenApiHttp.getJson(apiClient(), "/webrobot/api/categories")
    val cats     = if (catsNode != null && catsNode.isArray) catsNode.elements().asScala.toList else List.empty
    System.out.println()
    cats.zipWithIndex.foreach { case (c, i) =>
      val id   = Option(c.get("id")).map(_.asText("")).getOrElse("")
      val name = Option(c.get("name")).map(_.asText("")).getOrElse(id)
      System.out.println(s"  [${i + 1}] $name  ${ANSI_CYAN}($id)${ANSI_RESET}")
    }
    System.out.println()
    val catPick = WizardIO.prompt("Agent category (number or id, Enter for direct id)")

    val (agentId, catId) = if (catPick.nonEmpty) {
      val cid = try {
        val n = catPick.toInt
        if (n >= 1 && n <= cats.size) Option(cats(n - 1).get("id")).map(_.asText("")).getOrElse("") else catPick
      } catch { case _: NumberFormatException => catPick }
      val agentsNode = OpenApiHttp.getJson(apiClient(), "/webrobot/api/agents/" + apiClient().escapeString(cid))
      val agents     = if (agentsNode != null && agentsNode.isArray) agentsNode.elements().asScala.toList else List.empty
      if (agents.isEmpty) {
        System.out.println(s"  ${ANSI_YELLOW}No agents in this category.${ANSI_RESET}")
        (WizardIO.prompt("Agent id (manual)"), cid)
      } else {
        System.out.println()
        agents.zipWithIndex.foreach { case (a, i) =>
          val id   = Option(a.get("id")).map(_.asText("")).getOrElse("")
          val name = Option(a.get("name")).map(_.asText("")).getOrElse(id)
          System.out.println(s"  [${i + 1}] ${ANSI_BOLD}$name${ANSI_RESET}  ${ANSI_CYAN}($id)${ANSI_RESET}")
        }
        System.out.println()
        val agPick = WizardIO.prompt("Agent (number or id)")
        val aid = try {
          val n = agPick.toInt
          if (n >= 1 && n <= agents.size) Option(agents(n - 1).get("id")).map(_.asText("")).getOrElse("") else agPick
        } catch { case _: NumberFormatException => agPick }
        (aid, cid)
      }
    } else {
      val cid2 = WizardIO.prompt("Category id")
      val aid  = WizardIO.prompt("Agent id")
      (aid, cid2)
    }

    val code: String = try {
      val path = "/webrobot/api/agents/" + apiClient().escapeString(catId) +
                 "/" + apiClient().escapeString(agentId)
      val node = OpenApiHttp.getJson(apiClient(), path)
      if (node != null) Option(node.get("code")).map(_.asText("")).getOrElse("") else ""
    } catch { case _: Exception => "" }

    (catId, agentId, code)
  }

  // stageText: raw string representation of all stage args (scanned for $col patterns)
  // suggestedName: used as dataset name prefix
  // Returns Some(datasetId) or None if skipped
  protected def runDatasetWizard(stageText: String, suggestedName: String): Option[String] = {
    implicit val _cmd: BaseSubCommand = self
    val cols = extractColumns(stageText)
    WizardIO.header("Input Dataset")

    if (cols.isEmpty) {
      // No $col variables found — auto-generate single-row trigger dataset
      System.out.println(s"  ${ANSI_YELLOW}No $$col variables found in stages.${ANSI_RESET}")
      System.out.println(s"  The job requires an input dataset even without variables.")
      System.out.println(s"  Creating a trigger dataset with a single row (arbitrary value).\n")
      val name = WizardIO.prompt("Default dataset name", suggestedName + "-default-input")
      System.out.println(s"  ${ANSI_CYAN}Uploading...${ANSI_RESET}")
      val csv = "_trigger\n1\n"
      val result = uploadDatasetCsvMultipart(name, csv.getBytes("UTF-8"))
      result.foreach(id => System.out.println(s"  ${ANSI_GREEN}✓ Trigger dataset — id: $id${ANSI_RESET}"))
      result

    } else {
      System.out.println(s"  Variables found: ${cols.map(c => s"${ANSI_CYAN}$$$c${ANSI_RESET}").mkString("  ")}")
      System.out.println(s"  Required columns: ${ANSI_BOLD}${cols.mkString(", ")}${ANSI_RESET}")
      System.out.println(s"  Each dataset row = one workflow iteration.\n")
      System.out.println(s"  ${ANSI_CYAN}[1]${ANSI_RESET} Build new dataset (enter rows)")
      System.out.println(s"  ${ANSI_CYAN}[2]${ANSI_RESET} Use existing dataset (validate columns)")
      System.out.println(s"  ${ANSI_CYAN}[3]${ANSI_RESET} Skip\n")
      val choice = WizardIO.prompt("Choice", "1")

      choice match {
        case "2" =>
          val dsNode = OpenApiHttp.getJson(self.apiClient(), "/webrobot/api/datasets")
          val dsList = if (dsNode != null && dsNode.isArray) dsNode.elements().asScala.toList else List.empty
          if (dsList.isEmpty) {
            System.out.println(s"  ${ANSI_YELLOW}No datasets found.${ANSI_RESET}"); None
          } else {
            System.out.println()
            dsList.zipWithIndex.foreach { case (d, i) =>
              val id   = Option(d.get("id")).map(_.asText("")).getOrElse("")
              val name = Option(d.get("name")).map(_.asText("")).getOrElse(id)
              System.out.println(s"  [${i + 1}] ${ANSI_BOLD}$name${ANSI_RESET}  ${ANSI_CYAN}($id)${ANSI_RESET}")
            }
            System.out.println()
            val pick = WizardIO.prompt("Dataset (number or id)")
            val dsId = try {
              val n = pick.toInt
              if (n >= 1 && n <= dsList.size) Option(dsList(n - 1).get("id")).map(_.asText("")).getOrElse("") else pick
            } catch { case _: NumberFormatException => pick }
            if (dsId.isEmpty) None
            else {
              System.out.println(s"\n  ${ANSI_YELLOW}Make sure the dataset contains columns: ${ANSI_BOLD}${cols.mkString(", ")}${ANSI_RESET}")
              if (WizardIO.confirm("Are all columns present and correct?")) Some(dsId) else None
            }
          }

        case "3" => None

        case _ =>
          val csv       = buildCsvInteractive(cols)
          val rowCount  = csv.linesIterator.count(_.trim.nonEmpty) - 1
          if (rowCount <= 0) {
            System.out.println(s"  ${ANSI_YELLOW}No rows entered, dataset skipped.${ANSI_RESET}"); None
          } else {
            val name = WizardIO.prompt("Dataset name", suggestedName + "-input")
            System.out.println(s"  ${ANSI_CYAN}Uploading $rowCount row(s)...${ANSI_RESET}")
            val result = uploadDatasetCsvMultipart(name, csv.getBytes("UTF-8"))
            result.foreach(id => System.out.println(s"  ${ANSI_GREEN}✓ Dataset uploaded ($rowCount rows) — id: $id${ANSI_RESET}"))
            result
          }
      }
    }
  }
}

// ─── python extension wizard trait ───────────────────────────────────────────

trait PythonExtWizard { self: BaseSubCommand =>

  protected def stagePrefix(extType: String): String = extType match {
    case "resolver" => "python_resolver"
    case "action"   => "python_action"
    case _          => "python_row_transform"
  }

  private def extensionTemplate(extType: String, extName: String): String = extType match {
    case "resolver" =>
      s"def $extName(elem, attribute_name=None):\n    # elem is the raw attribute value\n    return elem"
    case "action" =>
      s"def $extName(page, params):\n    # page is the browser page object\n    return True"
    case _ =>
      s"def $extName(row):\n    # row is a dict; modify keys and return it\n    return row"
  }

  protected def promptExtensionFunction(extType: String, extName: String): String = {
    implicit val _cmd: BaseSubCommand = this
    val tmpl = extensionTemplate(extType, extName)
    System.out.println(s"\n  ${ANSI_BOLD}Template ($extType):${ANSI_RESET}")
    tmpl.linesIterator.foreach(l => System.out.println("    " + l))

    // LLM-assisted generation
    if (WizardIO.confirm("Generate code with LLM?")) {
      System.out.print(s"  Describe what the function should do: ")
      System.out.flush()
      val description = Option(scala.io.StdIn.readLine()).map(_.trim).getOrElse("")
      if (description.nonEmpty) {
        val systemPrompt = "You are a Python expert. Generate only the Python function body, no explanations, no markdown fences."
        val userPrompt = "Write a Python " + extType + " extension named '" + extName + "' for a PySpark ETL pipeline.\n" +
          "The function signature is: " + tmpl.linesIterator.next() + "\n" +
          "Description: " + description + "\n" +
          "Return only the complete Python function (def line + body), nothing else."
        System.out.println(s"  ${ANSI_CYAN}Calling LLM...${ANSI_RESET}")
        llmInfer(userPrompt, systemPrompt) match {
          case Some(generated) =>
            System.out.println(s"\n  ${ANSI_BOLD}Generated code:${ANSI_RESET}")
            generated.linesIterator.foreach(l => System.out.println("    " + l))
            if (WizardIO.confirm("\n  Use this code?")) return generated
            System.out.println(s"  ${ANSI_YELLOW}Code discarded — manual input.${ANSI_RESET}")
          case None =>
            System.out.println(s"  ${ANSI_YELLOW}LLM not available — manual input.${ANSI_RESET}")
        }
      }
    }

    System.out.println(s"\n  Enter the function body.")
    System.out.println(s"  Type ${ANSI_BOLD}---${ANSI_RESET} on an empty line to finish.\n")
    val lines = ListBuffer[String]()
    var reading = true
    while (reading) {
      val line = Option(scala.io.StdIn.readLine()).getOrElse("---")
      if (line.trim == "---") reading = false else lines += line
    }
    if (lines.isEmpty) tmpl else lines.mkString("\n")
  }

  protected def promptSingleExtension(): Option[(String, String, String)] = {
    implicit val _cmd: BaseSubCommand = this
    System.out.println(s"\n  ${ANSI_BOLD}Extension type:${ANSI_RESET}")
    System.out.println(s"  ${ANSI_CYAN}[1]${ANSI_RESET} row_transform  — transforms each dataset row")
    System.out.println(s"  ${ANSI_CYAN}[2]${ANSI_RESET} resolver       — resolves attributes (URL, values)")
    System.out.println(s"  ${ANSI_CYAN}[3]${ANSI_RESET} action         — browser/page action")
    System.out.println(s"  ${ANSI_CYAN}[0]${ANSI_RESET} Done\n")
    val typeChoice = WizardIO.prompt("Type", "1")
    if (typeChoice == "0") return None
    val extType = typeChoice match {
      case "2" => "resolver"
      case "3" => "action"
      case _   => "row_transform"
    }
    val extName = WizardIO.prompt("Extension name (e.g. price_normalizer)")
    if (extName.isEmpty) return None
    Some((extName, extType, promptExtensionFunction(extType, extName)))
  }

  protected def validateExtension(name: String, extType: String, fnCode: String): Boolean = {
    implicit val _cmd: BaseSubCommand = this
    try {
      val body = apiClient().getObjectMapper.createObjectNode()
      body.put("name", name); body.put("extensionType", extType); body.put("function", fnCode)
      val result = OpenApiHttp.postJson(apiClient(), "/webrobot/api/python-extensions/validate", body)
      if (result == null) return true
      val valid = result.path("valid").asBoolean(true)
      if (!valid) System.out.println(s"  ${ANSI_RED}Validation failed: ${result.path("error").asText(result.path("message").asText(""))}${ANSI_RESET}")
      else System.out.println(s"  ${ANSI_GREEN}✓ Validation OK${ANSI_RESET}")
      valid
    } catch { case _: Exception => System.out.println(s"  ${ANSI_YELLOW}Validation not available.${ANSI_RESET}"); true }
  }

  protected def registerExtension(agentId: String, name: String, extType: String, fnCode: String): Option[String] = {
    implicit val _cmd: BaseSubCommand = this
    try {
      val body = apiClient().getObjectMapper.createObjectNode()
      body.put("name", name); body.put("agentId", agentId)
      body.put("extensionType", extType); body.put("function", fnCode)
      val result = OpenApiHttp.postJson(apiClient(),
        "/webrobot/api/python-extensions/agents/" + apiClient().escapeString(agentId) + "/python-extensions", body)
      val id = result.path("id").asText("")
      if (id.nonEmpty) Some(id) else None
    } catch { case e: Exception => System.out.println(s"  ${ANSI_RED}Error: ${e.getMessage}${ANSI_RESET}"); None }
  }

  // Builds python_extensions: YAML block from collected (name, type, code)
  protected def buildPythonExtBlock(exts: List[(String, String, String)]): String = {
    if (exts.isEmpty) return ""
    val sb = new StringBuilder("\npython_extensions:\n  stages:\n")
    exts.foreach { case (name, extType, code) =>
      sb.append(s"    $name:\n      type: $extType\n      function: |\n")
      code.linesIterator.foreach(l => sb.append("        " + l + "\n"))
    }
    sb.toString
  }

  // For agent code YAML: collect extensions, insert stages + python_extensions block
  protected def addInlineExtensionsToAgentYaml(yaml: String): String = {
    implicit val _cmd: BaseSubCommand = this
    if (!WizardIO.confirm("Add Python extensions inline to the code?")) return yaml
    val exts = ListBuffer[(String, String, String)]()
    var adding = true
    while (adding) {
      promptSingleExtension() match {
        case Some(ext) =>
          exts += ext
          System.out.println(s"  ${ANSI_GREEN}✓ '${ext._1}' added.${ANSI_RESET}")
          adding = WizardIO.confirm("Add another extension?")
        case None => adding = false
      }
    }
    if (exts.isEmpty) return yaml
    val stageLines = exts.map { case (n, t, _) => s"  - stage: ${stagePrefix(t)}:$n\n    args: []\n" }.mkString
    yaml + stageLines + buildPythonExtBlock(exts.toList)
  }

  // For pipeline manifest: adds stages to pd, returns python_extensions block to append to file
  protected def addInlineExtensionsToPipeline(pd: scala.collection.mutable.Map[String, Any]): String = {
    implicit val _cmd: BaseSubCommand = this
    if (!WizardIO.confirm("Add Python extensions inline to the pipeline?")) return ""
    val exts = ListBuffer[(String, String, String)]()
    var adding = true
    while (adding) {
      promptSingleExtension() match {
        case Some(ext @ (name, extType, _)) =>
          exts += ext
          YamlManifest.addStage(pd, s"${stagePrefix(extType)}:$name", Some(List.empty), Map.empty, None, AtEnd)
          System.out.println(s"  ${ANSI_GREEN}✓ Stage '${stagePrefix(extType)}:$name' added.${ANSI_RESET}")
          adding = WizardIO.confirm("Add another extension?")
        case None => adding = false
      }
    }
    buildPythonExtBlock(exts.toList)
  }
}

// ─── agent wizard ─────────────────────────────────────────────────────────────

@Command(
  name = "agent",
  sortOptions = false,
  description = Array("Interactive wizard to create a new agent.")
)
class AgentWizardCommand extends BaseSubCommand with PythonExtWizard {

  override def startRun(): Unit = {
    this.init()
    implicit val self: BaseSubCommand = this

    WizardIO.header("Wizard — New Agent")

    // 1. Categories
    System.out.println(s"  ${ANSI_CYAN}Loading categories...${ANSI_RESET}")
    val catsNode = OpenApiHttp.getJson(apiClient(), "/webrobot/api/categories")
    if (catsNode == null || !catsNode.isArray || catsNode.size() == 0) {
      System.out.println(s"  ${ANSI_RED}No categories found.${ANSI_RESET}"); return
    }
    val cats = catsNode.elements().asScala.toList
    System.out.println()
    cats.zipWithIndex.foreach { case (c, i) =>
      val id   = Option(c.get("id")).map(_.asText("")).getOrElse("")
      val name = Option(c.get("name")).map(_.asText("")).getOrElse(id)
      System.out.println(s"  [${i + 1}] $name  ${ANSI_CYAN}($id)${ANSI_RESET}")
    }
    System.out.println()

    // 2. Category selection
    val catInput = WizardIO.prompt("Category (number or id)")
    val categoryId = try {
      val n = catInput.toInt
      if (n >= 1 && n <= cats.size) Option(cats(n - 1).get("id")).map(_.asText("")).getOrElse("") else catInput
    } catch { case _: NumberFormatException => catInput }
    if (categoryId.isEmpty) { System.out.println(s"  ${ANSI_RED}Invalid category.${ANSI_RESET}"); return }

    // 3. Name and description
    val agentName = WizardIO.prompt("Agent name")
    if (agentName.isEmpty) { System.out.println(s"  ${ANSI_RED}Name is required.${ANSI_RESET}"); return }
    val agentDesc = WizardIO.prompt("Description")

    // 4. Agent code
    WizardIO.header("Agent code")
    System.out.println(s"  ${ANSI_CYAN}[1]${ANSI_RESET} Compose interactively (stage wizard)")
    System.out.println(s"  ${ANSI_CYAN}[2]${ANSI_RESET} Provide an existing YAML file")
    System.out.println(s"  ${ANSI_CYAN}[3]${ANSI_RESET} Skip (add code later)\n")
    val codeChoice = WizardIO.prompt("Choice", "1")

    var savedCodeFile: Option[String] = None

    val codeContent: Option[String] = codeChoice match {

      case "2" =>
        val codeFile = WizardIO.prompt("Code file (path)")
        if (codeFile.nonEmpty) {
          val f = new File(codeFile)
          if (!f.exists()) {
            System.out.println(s"  ${ANSI_YELLOW}File not found, code skipped.${ANSI_RESET}"); None
          } else {
            savedCodeFile = Some(codeFile)
            Some(scala.io.Source.fromFile(f).getLines().mkString("\r\n"))
          }
        } else None

      case "3" => None

      case _ =>
        try { StageCatalog.fetchRemote(apiClient()) } catch { case _: Exception => }
        System.out.println(s"\n  Categories: ${ANSI_CYAN}${StageCatalog.categories.mkString(" | ")}${ANSI_RESET}")

        val collectedStages = ListBuffer[(String, List[String])]()

        // Optional Browser Use probe step
        val probeContext: Option[String] = {
          val probeAvail = BrowserUseProbe.isAvailable
          val label = if (probeAvail) "Browser Use is available." else "Browser Use not detected (install with: pip install browser-use)."
          System.out.println(s"  ${ANSI_CYAN}$label${ANSI_RESET}")
          if (probeAvail && WizardIO.confirm("Probe the target URL with Browser Use to discover fields and suggest stages?")) {
            val targetUrl = WizardIO.prompt("Target URL to probe")
            val probeGoal = WizardIO.prompt("Probe objective (e.g. 'extract product price and title')", "explore the page and identify key data fields")
            if (targetUrl.nonEmpty) {
              System.out.println(s"  ${ANSI_CYAN}Launching Browser Use probe (up to 90s)...${ANSI_RESET}")
              BrowserUseProbe.run(targetUrl, probeGoal) match {
                case Some(result) =>
                  val summary = BrowserUseProbe.summarize(result)
                  System.out.println(s"\n  ${ANSI_BOLD}Browser Use found:${ANSI_RESET}")
                  summary.linesIterator.foreach(l => System.out.println("  " + l))
                  System.out.println()
                  Some(summary)
                case None =>
                  System.out.println(s"  ${ANSI_YELLOW}Probe returned no results — continuing without it.${ANSI_RESET}")
                  None
              }
            } else None
          } else None
        }

        // LLM natural-language mode
        System.out.print(s"  Describe the agent in natural language (or Enter to add stages manually): ")
        System.out.flush()
        val nlDesc = Option(scala.io.StdIn.readLine()).map(_.trim).getOrElse("")
        if (nlDesc.nonEmpty) {
          StageWizardHelper.suggestStagesFromDescription(nlDesc, probeContext).foreach(collectedStages += _)
          System.out.println(s"\n  You can continue adding stages manually.\n")
        } else {
          System.out.println(s"  Type stage name or category; empty Enter to finish.\n")
        }

        var adding = true
        while (adding) {
          if (collectedStages.nonEmpty) {
            System.out.println(s"  ${ANSI_BOLD}Added stages:${ANSI_RESET}")
            collectedStages.zipWithIndex.foreach { case ((n, a), i) =>
              val argsStr = if (a.nonEmpty) s"  args: [${a.mkString(", ")}]" else ""
              System.out.println(s"  ${ANSI_CYAN}  [${i + 1}] $n${ANSI_RESET}$argsStr")
            }
            System.out.println()
          }
          System.out.print(s"  ${ANSI_BOLD}Stage [${collectedStages.size + 1}]${ANSI_RESET} (name / category / Enter to finish): ")
          System.out.flush()
          val input = Option(scala.io.StdIn.readLine()).map(_.trim).getOrElse("")
          if (input.isEmpty) {
            adding = false
          } else {
            val stageName = StageWizardHelper.resolveStageInput(input)
            if (stageName.nonEmpty) {
              StageWizardHelper.collectStageArgs(stageName) match {
                case Some(entry) =>
                  collectedStages += entry
                  System.out.println(s"  ${ANSI_GREEN}✓ Stage '${entry._1}' added.${ANSI_RESET}\n")
                case None =>
              }
            }
          }
        }

        if (collectedStages.isEmpty) None
        else {
          var yaml = StageWizardHelper.stagesToYaml(collectedStages.toList)
          yaml = addInlineExtensionsToAgentYaml(yaml)
          System.out.println(s"\n  ${ANSI_BOLD}Generated YAML code:${ANSI_RESET}")
          yaml.linesIterator.foreach(l => System.out.println("    " + l))
          val saveFile = WizardIO.prompt("\n  Save code to file (leave empty to skip)")
          if (saveFile.nonEmpty) {
            val f = new File(saveFile)
            java.nio.file.Files.write(f.toPath, yaml.getBytes("UTF-8"))
            System.out.println(s"  ${ANSI_GREEN}Saved: ${f.getPath}${ANSI_RESET}")
            savedCodeFile = Some(saveFile)
          }
          Some(yaml)
        }
    }

    // 4.5 Input dataset: ask if the pipeline reads from a dataset and prepend load_csv
    System.out.println()
    val useDataset = WizardIO.confirm("Does the pipeline read data from an input dataset (e.g. URL list)?")
    val loadCsvYaml = "stages:\n  - stage: load_csv\n    args:\n      - \"${INPUT_CSV_PATH}\"\n"
    val finalCodeContent: Option[String] = if (useDataset) {
      val withLoad = codeContent.map { yaml =>
        if (yaml.startsWith("stages:\n"))
          loadCsvYaml + yaml.substring("stages:\n".length)
        else
          loadCsvYaml + yaml
      }.orElse(Some(loadCsvYaml))
      System.out.println(s"  ${ANSI_GREEN}✓ Stage load_csv ($${INPUT_CSV_PATH}) prepended as first stage.${ANSI_RESET}")
      System.out.println(s"  ${ANSI_YELLOW}Remember: associate a CSV dataset when creating the job (wizard job).${ANSI_RESET}\n")
      withLoad
    } else codeContent

    // 5. Comando equivalente e conferma
    val fileFlag = savedCodeFile.map(f => s" -f $f").getOrElse("")
    val descFlag = if (agentDesc.nonEmpty) s""" -d "$agentDesc"""" else ""
    WizardIO.showCommand(this, s"""webrobot agent add -c $categoryId -n "$agentName"$descFlag$fileFlag""")

    if (!WizardIO.confirm("Create the agent?")) { System.out.println("  Cancelled."); return }

    // 6. Execute
    val dto = new AgentDto()
    dto.setName(agentName)
    dto.setDescription(agentDesc)
    dto.setCategoryId(categoryId)
    finalCodeContent.foreach(dto.setCode)

    val result = OpenApiHttp.postJson(apiClient(), "/webrobot/api/agents", dto)
    val newId  = if (result != null) Option(result.get("id")).map(_.asText("")).getOrElse("") else ""

    System.out.println(s"\n  ${ANSI_GREEN}Agent created!${ANSI_RESET}")
    if (newId.nonEmpty) System.out.println(s"  id: ${ANSI_BOLD}$newId${ANSI_RESET}")
    System.out.println(s"\n  Next steps:")
    System.out.println(s"  ${ANSI_CYAN}webrobot wizard job${ANSI_RESET}  — create the job and input dataset")
  }
}

// ─── pipeline wizard ──────────────────────────────────────────────────────────

@Command(
  name = "pipeline",
  sortOptions = false,
  description = Array("Interactive wizard to create and run a pipeline.")
)
class PipelineWizardCommand extends BaseSubCommand with DatasetWizard with PythonExtWizard {

  @Opt(names = Array("-f", "--file"), description = Array("YAML output file (default: pipeline.yaml)"))
  private var outputFile: String = "pipeline.yaml"

  @Opt(names = Array("-F", "--follow"), description = Array("Follow execution after start"))
  private var follow: Boolean = false

  override def startRun(): Unit = {
    this.init()
    implicit val self: BaseSubCommand = this

    try { StageCatalog.fetchRemote(apiClient()) } catch { case _: Exception => }

    WizardIO.header("Wizard — New Pipeline")

    // ── 1. Project ─────────────────────────────────────────────────────────
    System.out.println(s"  ${ANSI_CYAN}Loading projects...${ANSI_RESET}")
    val projNode = OpenApiHttp.getJson(apiClient(), "/webrobot/api/projects")
    val projects = if (projNode != null && projNode.isArray) projNode.elements().asScala.toList else List.empty
    if (projects.isEmpty) { System.out.println(s"  ${ANSI_RED}No projects found.${ANSI_RESET}"); return }
    System.out.println()
    projects.zipWithIndex.foreach { case (p, i) =>
      val id   = Option(p.get("id")).map(_.asText("")).getOrElse("")
      val name = Option(p.get("name")).map(_.asText("")).getOrElse(id)
      System.out.println(s"  [${i + 1}] ${ANSI_BOLD}$name${ANSI_RESET}  ${ANSI_CYAN}($id)${ANSI_RESET}")
    }
    System.out.println()
    val projInput  = WizardIO.prompt("Project (number or id)")
    val projectRef = try {
      val n = projInput.toInt
      if (n >= 1 && n <= projects.size) Option(projects(n - 1).get("id")).map(_.asText("")).getOrElse("") else projInput
    } catch { case _: NumberFormatException => projInput }
    if (projectRef.isEmpty) { System.out.println(s"  ${ANSI_RED}Invalid project.${ANSI_RESET}"); return }

    // ── 2. Metadata and file ───────────────────────────────────────────────
    System.out.println()
    val pipelineName = WizardIO.prompt("Pipeline name")
    if (pipelineName.isEmpty) { System.out.println(s"  ${ANSI_RED}Name is required.${ANSI_RESET}"); return }
    val pipelineDesc = WizardIO.prompt("Description (optional)")
    val yamlPath     = WizardIO.prompt("YAML output file", outputFile)
    val file         = new File(yamlPath)

    val (docs, pd) = YamlManifest.ensurePipeline(file)
    val meta = pd.getOrElseUpdate("metadata", scala.collection.mutable.Map[String, Any]())
                 .asInstanceOf[scala.collection.mutable.Map[String, Any]]
    meta("name") = pipelineName
    if (pipelineDesc.nonEmpty) meta("description") = pipelineDesc
    val spec = pd.getOrElseUpdate("spec", scala.collection.mutable.Map[String, Any]())
                 .asInstanceOf[scala.collection.mutable.Map[String, Any]]
    spec("project") = projectRef

    // ── 3. Stage loop ──────────────────────────────────────────────────────
    WizardIO.header("Pipeline composition — Stages")
    System.out.println(s"  Available categories: ${ANSI_CYAN}${StageCatalog.categories.mkString(" | ")}${ANSI_RESET}")

    // LLM natural-language mode
    System.out.print(s"  Describe the pipeline in natural language (or Enter to add stages manually): ")
    System.out.flush()
    val nlDesc = Option(scala.io.StdIn.readLine()).map(_.trim).getOrElse("")
    var stageCount = 0
    if (nlDesc.nonEmpty) {
      StageWizardHelper.suggestStagesFromDescription(nlDesc).foreach { entry =>
        YamlManifest.addStage(pd, entry._1, if (entry._2.nonEmpty) Some(entry._2) else None, Map.empty, None,
          org.webrobot.cli.manifest.AtEnd)
        stageCount += 1
      }
      if (stageCount > 0)
        System.out.println(s"\n  ${ANSI_GREEN}✓ $stageCount stage(s) added from LLM.${ANSI_RESET}")
      System.out.println(s"\n  You can continue adding stages manually.\n")
    } else {
      System.out.println(s"  Type a stage name, a category to filter, or Enter to finish.\n")
    }

    var addingStages = true

    while (addingStages) {
      showCurrentPipeline(pd, stageCount)
      System.out.print(s"  ${ANSI_BOLD}Stage [${stageCount + 1}]${ANSI_RESET} (stage name / category / Enter to finish): ")
      System.out.flush()
      val input = Option(scala.io.StdIn.readLine()).map(_.trim).getOrElse("")

      if (input.isEmpty) {
        addingStages = false
      } else {
        val stageName = StageWizardHelper.resolveStageInput(input)
        if (stageName.nonEmpty && addStageToManifest(pd, stageName)) stageCount += 1
      }
    }

    // ── 4. Python extensions inline ────────────────────────────────────────
    val pyExtBlock = addInlineExtensionsToPipeline(pd)

    // ── 5. Dataset di input (analisi $col dagli stage) ─────────────────────
    val stageText = {
      val sl = YamlManifest.stageList(pd)
      (0 until sl.size()).map(sl.get).mkString(" ")
    }
    val inputDatasetId = runDatasetWizard(stageText, pipelineName)
    inputDatasetId.foreach(id => YamlManifest.setInput(pd, Some(id), None))

    // ── 6. Output ──────────────────────────────────────────────────────────
    WizardIO.header("Output")
    val outputFormat = WizardIO.prompt("Format (parquet / csv / json)", "parquet")
    val outputMode   = WizardIO.prompt("Write mode (overwrite / append)", "overwrite")
    YamlManifest.setOutput(pd, Some(outputFormat), Some(outputMode), None)

    // ── 7. Schedule ────────────────────────────────────────────────────────
    val schedCron = WizardIO.prompt("Schedule cron (e.g. '0 6 * * *', leave empty for none)")
    if (schedCron.nonEmpty) {
      val tz = WizardIO.prompt("Timezone", "Europe/Rome")
      YamlManifest.setSchedule(pd, schedCron, tz)
    }

    // ── 8. Save + append python_extensions if present ─────────────────────
    YamlManifest.save(file, docs)
    if (pyExtBlock.nonEmpty) {
      val fw = new java.io.FileWriter(file, true)
      try { fw.write(pyExtBlock) } finally { fw.close() }
    }
    System.out.println(s"\n${ANSI_BOLD}  Pipeline saved: ${ANSI_CYAN}${file.getPath}${ANSI_RESET}")
    System.out.println(s"\n${ANSI_BOLD}  YAML preview:${ANSI_RESET}")
    new String(java.nio.file.Files.readAllBytes(file.toPath), "UTF-8")
      .linesIterator.foreach(l => System.out.println("    " + l))
    WizardIO.showCommand(this, s"webrobot pipeline run -f ${file.getName} --follow")

    // ── 9. Apply ───────────────────────────────────────────────────────────
    if (!WizardIO.confirm("Apply the manifest to the server?")) {
      System.out.println(s"  ${ANSI_CYAN}webrobot pipeline apply -f ${file.getName}${ANSI_RESET}")
      return
    }
    val yamlContent = new String(java.nio.file.Files.readAllBytes(file.toPath), "UTF-8")
    System.out.println(s"  ${ANSI_CYAN}Applying manifest...${ANSI_RESET}")
    val applyBody = new java.util.HashMap[String, String]()
    applyBody.put("yaml", yamlContent)
    val applyNode = OpenApiHttp.postJson(apiClient(), "/webrobot/api/manifest/apply", applyBody)
    val projectId = findJsonField(applyNode, "projectId")
    val jobId     = findJsonField(applyNode, "jobId")
    if (projectId == null || jobId == null) {
      System.out.println(s"  ${ANSI_YELLOW}Manifest applied but projectId/jobId not extracted.${ANSI_RESET}")
      JsonCliUtil.printJson(applyNode)
      return
    }
    System.out.println(s"  ${ANSI_GREEN}✓ Applied — project: $projectId  job: $jobId${ANSI_RESET}")

    // ── 10. Run ────────────────────────────────────────────────────────────
    if (!WizardIO.confirm("Start execution now?")) {
      System.out.println(s"  ${ANSI_CYAN}webrobot job execute -p $projectId -j $jobId --follow${ANSI_RESET}")
      return
    }
    val execPath = "/webrobot/api/projects/id/" + apiClient().escapeString(projectId) +
                  "/jobs/" + apiClient().escapeString(jobId) + "/execute"
    val execNode = OpenApiHttp.postJson(apiClient(), execPath,
                    com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode())
    val execId   = extractJsonField(execNode, "executionId", "id", "execution_id", "executionReferenceId")
    if (execId == null || execId.isEmpty) {
      System.out.println(s"  ${ANSI_YELLOW}Execution started but executionId not found.${ANSI_RESET}")
      JsonCliUtil.printJson(execNode)
      return
    }
    System.out.println(s"  ${ANSI_GREEN}✓ Execution started — id: ${ANSI_BOLD}$execId${ANSI_RESET}")
    if (follow) followExecution(projectId, jobId, execId)
    else System.out.println(s"  ${ANSI_CYAN}webrobot job execute -p $projectId -j $jobId -F --follow${ANSI_RESET}")
  }

  private def addStageToManifest(pd: scala.collection.mutable.Map[String, Any], stageName: String): Boolean = {
    implicit val self: BaseSubCommand = this
    StageWizardHelper.collectStageArgs(stageName) match {
      case Some((name, args)) =>
        YamlManifest.addStage(pd, name, if (args.nonEmpty) Some(args) else None, Map.empty, None, AtEnd)
        System.out.println(s"  ${ANSI_GREEN}✓ Stage '$name' added.${ANSI_RESET}\n")
        true
      case None => false
    }
  }

  private def showCurrentPipeline(pd: scala.collection.mutable.Map[String, Any], count: Int): Unit = {
    if (count == 0) return
    val stages = YamlManifest.stageList(pd)
    System.out.println(s"  ${ANSI_BOLD}Current pipeline ($count stage(s)):${ANSI_RESET}")
    (0 until stages.size()).foreach { i =>
      stages.get(i) match {
        case m: java.util.Map[_, _] =>
          val sm   = m.asInstanceOf[java.util.Map[String, Any]]
          val s    = Option(sm.get("stage")).map(_.toString).getOrElse("?")
          val args = Option(sm.get("args")).map(a => s"  args: $a").getOrElse("")
          System.out.println(s"  ${ANSI_CYAN}  [$i] $s${ANSI_RESET}$args")
        case _ =>
      }
    }
    System.out.println()
  }
}

// ─── job wizard ───────────────────────────────────────────────────────────────

@Command(
  name = "job",
  sortOptions = false,
  description = Array("Interactive wizard to create a job, configure the input dataset and run.")
)
class JobWizardCommand extends BaseSubCommand with DatasetWizard {

  @Opt(names = Array("-F", "--follow"), description = Array("Follow execution after start"))
  private var follow: Boolean = false

  override def startRun(): Unit = {
    this.init()
    implicit val self: BaseSubCommand = this

    WizardIO.header("Wizard — New Job")

    // ── 1. Project ─────────────────────────────────────────────────────────
    System.out.println(s"  ${ANSI_CYAN}Loading projects...${ANSI_RESET}")
    val projNode = OpenApiHttp.getJson(apiClient(), "/webrobot/api/projects")
    val projects = if (projNode != null && projNode.isArray) projNode.elements().asScala.toList else List.empty
    if (projects.isEmpty) { System.out.println(s"  ${ANSI_RED}No projects found.${ANSI_RESET}"); return }
    System.out.println()
    projects.zipWithIndex.foreach { case (p, i) =>
      val id   = Option(p.get("id")).map(_.asText("")).getOrElse("")
      val name = Option(p.get("name")).map(_.asText("")).getOrElse(id)
      System.out.println(s"  [${i + 1}] ${ANSI_BOLD}$name${ANSI_RESET}  ${ANSI_CYAN}($id)${ANSI_RESET}")
    }
    System.out.println()
    val projInput = WizardIO.prompt("Project (number or id)")
    val projectId = try {
      val n = projInput.toInt
      if (n >= 1 && n <= projects.size) Option(projects(n - 1).get("id")).map(_.asText("")).getOrElse("") else projInput
    } catch { case _: NumberFormatException => projInput }
    if (projectId.isEmpty) { System.out.println(s"  ${ANSI_RED}Invalid project.${ANSI_RESET}"); return }

    // ── 2. Agent (by category) + fetch code for $col detection ───────────
    System.out.println()
    val (_, agentId, agentCode) = selectAgentWithCode()
    if (agentId.isEmpty) { System.out.println(s"  ${ANSI_RED}Agent is required.${ANSI_RESET}"); return }

    // ── 3. Job name and description ────────────────────────────────────────
    System.out.println()
    val jobName = WizardIO.prompt("Job name")
    if (jobName.isEmpty) { System.out.println(s"  ${ANSI_RED}Name is required.${ANSI_RESET}"); return }
    val jobDesc = WizardIO.prompt("Description (optional)")

    // ── 4. Dataset wizard ──────────────────────────────────────────────────
    val datasetId = runDatasetWizard(agentCode, jobName)
    if (datasetId.isEmpty) {
      System.out.println(s"  ${ANSI_YELLOW}Dataset not configured — cannot create the job.${ANSI_RESET}"); return
    }

    // ── 5. Create job ──────────────────────────────────────────────────────
    WizardIO.showCommand(this,
      s"""webrobot job add -p $projectId -n "$jobName" -a $agentId -i ${datasetId.get}""")
    if (!WizardIO.confirm("Create the job?")) { System.out.println("  Cancelled."); return }

    val dto = new JobDto()
    dto.setProjectId(projectId)
    dto.setName(jobName)
    if (jobDesc.nonEmpty) dto.setDescription(jobDesc)
    dto.setAgentId(agentId)
    dto.setInputDatasetId(datasetId.get)
    val jobNode = OpenApiHttp.postJson(apiClient(),
      "/webrobot/api/projects/id/" + apiClient().escapeString(projectId) + "/jobs", dto)
    val jobId = if (jobNode != null) Option(jobNode.get("id")).map(_.asText("")).getOrElse("") else ""
    if (jobId.isEmpty) {
      System.out.println(s"  ${ANSI_YELLOW}Job created but id not extracted.${ANSI_RESET}")
      JsonCliUtil.printJson(jobNode)
      return
    }
    System.out.println(s"\n  ${ANSI_GREEN}✓ Job created — id: ${ANSI_BOLD}$jobId${ANSI_RESET}")

    // ── 6. Run? ────────────────────────────────────────────────────────────
    if (!WizardIO.confirm("Start execution now?")) {
      System.out.println(s"  ${ANSI_CYAN}webrobot job execute -p $projectId -j $jobId --follow${ANSI_RESET}")
      return
    }
    val execPath = "/webrobot/api/projects/id/" + apiClient().escapeString(projectId) +
                  "/jobs/" + apiClient().escapeString(jobId) + "/execute"
    val execNode = OpenApiHttp.postJson(apiClient(), execPath,
                    com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode())
    val execId   = extractJsonField(execNode, "executionId", "id", "execution_id", "executionReferenceId")
    if (execId == null || execId.isEmpty) {
      System.out.println(s"  ${ANSI_YELLOW}Execution started but executionId not found.${ANSI_RESET}")
      JsonCliUtil.printJson(execNode)
      return
    }
    System.out.println(s"  ${ANSI_GREEN}✓ Execution started — id: ${ANSI_BOLD}$execId${ANSI_RESET}")
    if (follow) followExecution(projectId, jobId, execId)
    else System.out.println(s"  ${ANSI_CYAN}webrobot job execute -p $projectId -j $jobId --follow${ANSI_RESET}")
  }
}

// ─── dataset wizard ───────────────────────────────────────────────────────────

@Command(
  name = "dataset",
  sortOptions = false,
  description = Array("Interactive wizard to create and upload an input dataset, guided by the reference agent.")
)
class DatasetWizardCommand extends BaseSubCommand with DatasetWizard {

  override def startRun(): Unit = {
    this.init()
    implicit val self: BaseSubCommand = this

    WizardIO.header("Wizard — Input Dataset")
    System.out.println("  Select the reference agent to detect $col variables.\n")

    val (_, _, agentCode) = selectAgentWithCode()

    System.out.println()
    val dsName = WizardIO.prompt("Dataset name", "input-dataset")

    val dsId = runDatasetWizard(agentCode, dsName)
    dsId match {
      case Some(id) =>
        System.out.println(s"\n  ${ANSI_GREEN}Dataset ready!${ANSI_RESET}")
        System.out.println(s"  id: ${ANSI_BOLD}$id${ANSI_RESET}")
        System.out.println(s"\n  Next steps:")
        System.out.println(s"  ${ANSI_CYAN}webrobot wizard job${ANSI_RESET}  — create the job linking this dataset")
        System.out.println(s"  ${ANSI_CYAN}webrobot job add -p <projectId> -n <name> -a <agentId> -i $id${ANSI_RESET}")
      case None =>
        System.out.println(s"  ${ANSI_YELLOW}No dataset created.${ANSI_RESET}")
    }
  }
}

// ─── python extension wizard ──────────────────────────────────────────────────

@Command(
  name = "python-ext",
  sortOptions = false,
  description = Array("Wizard to create and register a Python Extension in an agent (Mode B — from database).")
)
class PythonExtWizardCommand extends BaseSubCommand with DatasetWizard with PythonExtWizard {

  override def startRun(): Unit = {
    this.init()
    implicit val self: BaseSubCommand = this

    WizardIO.header("Wizard — Python Extension")
    System.out.println("  Register a reusable Python Extension (Mode B).")
    System.out.println("  The extension is associated with an agent and can be referenced by name in the pipeline YAML.\n")

    // 1. Select agent
    val (_, agentId, _) = selectAgentWithCode()
    if (agentId.isEmpty) { System.out.println(s"  ${ANSI_RED}Agent is required.${ANSI_RESET}"); return }

    // 2. Define extension
    WizardIO.header("Extension Definition")
    val extOpt = promptSingleExtension()
    if (extOpt.isEmpty) { System.out.println("  Cancelled."); return }
    val (extName, extType, fnCode) = extOpt.get
    val stageName = s"${stagePrefix(extType)}:$extName"

    // 3. Inline YAML preview
    System.out.println(s"\n  ${ANSI_BOLD}Inline block preview (Mode A):${ANSI_RESET}")
    buildPythonExtBlock(List((extName, extType, fnCode)))
      .linesIterator.foreach(l => System.out.println("    " + l))

    // 4. Validate
    System.out.println(s"\n  ${ANSI_CYAN}Validating...${ANSI_RESET}")
    val valid = validateExtension(extName, extType, fnCode)
    if (!valid && !WizardIO.confirm("Register anyway?")) { System.out.println("  Cancelled."); return }

    // 5. Register
    WizardIO.showCommand(this,
      s"""webrobot python-ext add -a $agentId -n "$extName" --extension-type $extType""")
    if (!WizardIO.confirm("Register the extension?")) { System.out.println("  Cancelled."); return }

    System.out.println(s"  ${ANSI_CYAN}Registering...${ANSI_RESET}")
    registerExtension(agentId, extName, extType, fnCode) match {
      case Some(id) =>
        System.out.println(s"\n  ${ANSI_GREEN}✓ Extension registered — id: ${ANSI_BOLD}$id${ANSI_RESET}")
        System.out.println(s"\n  Use this stage in the pipeline YAML:")
        System.out.println(s"  ${ANSI_CYAN}  - stage: $stageName${ANSI_RESET}")
        System.out.println(s"  ${ANSI_CYAN}    args: []${ANSI_RESET}")
        System.out.println(s"\n  Useful commands:")
        System.out.println(s"  ${ANSI_CYAN}webrobot python-ext list -a $agentId${ANSI_RESET}")
        System.out.println(s"  ${ANSI_CYAN}webrobot python-ext generate-pyspark -i $id${ANSI_RESET}")
      case None =>
        System.out.println(s"  ${ANSI_YELLOW}Extension not registered.${ANSI_RESET}")
    }
  }
}

// ─── wizard (gruppo) ──────────────────────────────────────────────────────────

@Command(
  name = "wizard",
  mixinStandardHelpOptions = true,
  sortOptions = false,
  description = Array("Guided interactive wizards to create resources step by step."),
  footer = Array(
    "",
    "Examples:",
    "  webrobot wizard agent             -- create an agent with stage composer + inline extensions",
    "  webrobot wizard pipeline          -- create pipeline + extensions + dataset + run",
    "  webrobot wizard job               -- create job + input dataset + run",
    "  webrobot wizard dataset           -- create/upload dataset guided by agent",
    "  webrobot wizard python-ext        -- register Python Extension in an agent (Mode B)",
    "  webrobot wizard pipeline -f my.yaml --follow",
    ""
  ),
  subcommands = Array(
    classOf[AgentWizardCommand],
    classOf[PipelineWizardCommand],
    classOf[JobWizardCommand],
    classOf[DatasetWizardCommand],
    classOf[PythonExtWizardCommand]
  )
)
class RunWizardCommand extends Runnable {
  def run(): Unit = System.err.println("Usage: webrobot wizard <agent|pipeline|job|dataset|python-ext>")
}
