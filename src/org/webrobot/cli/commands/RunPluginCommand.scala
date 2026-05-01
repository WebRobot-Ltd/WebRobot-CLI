package org.webrobot.cli.commands

import picocli.CommandLine.{Command, Option, Parameters}

import java.io.{File, PrintWriter}
import java.nio.file.{Files, Path, Paths}
import scala.io.Source

// ──────────────────────────────────────────────────────────────────────────────
// webrobot plugin  (root group)
// ──────────────────────────────────────────────────────────────────────────────

@Command(
  name        = "plugin",
  description = Array("Software factory per plugin ETL WebRobot."),
  subcommands = Array(
    classOf[PluginNewCommand],
    classOf[PluginAddCommand]
  ),
  mixinStandardHelpOptions = true
)
class RunPluginCommand extends Runnable {
  override def run(): Unit = ()
}

// ──────────────────────────────────────────────────────────────────────────────
// webrobot plugin new <nome>
// ──────────────────────────────────────────────────────────────────────────────

@Command(
  name        = "new",
  description = Array("Genera un nuovo progetto plugin Gradle/Scala pronto per l'ETL WebRobot."),
  mixinStandardHelpOptions = true
)
class PluginNewCommand extends Runnable {

  @Parameters(index = "0", description = Array("Nome del plugin (es. my-etl-plugin)"))
  private var pluginName: String = ""

  @Option(names = Array("-g", "--group"), description = Array("Group ID Maven (default: eu.webrobot.plugins)"))
  private var groupId: String = "eu.webrobot.plugins"

  @Option(names = Array("-o", "--output"), description = Array("Directory di output (default: ./<pluginName>)"))
  private var outputDir: String = ""

  @Option(names = Array("--sdk-version"), description = Array("Versione webrobot-plugin-sdk (default: latest)"))
  private var sdkVersion: String = "latest.release"

  override def run(): Unit = {
    val dir = if (outputDir.nonEmpty) Paths.get(outputDir) else Paths.get(pluginName)
    if (Files.exists(dir)) {
      System.err.println(s"Errore: la directory '$dir' esiste già.")
      System.exit(1)
    }

    val pkg = groupId + "." + pluginName.replace("-", "")

    createDirs(dir, pkg)
    PluginHelpers.writeFile(dir.resolve("settings.gradle.kts"), PluginTemplates.settings(pluginName))
    PluginHelpers.writeFile(dir.resolve("build.gradle.kts"),    PluginTemplates.buildGradle(groupId, pluginName, sdkVersion))
    PluginHelpers.writeFile(dir.resolve("gradle.properties"),   PluginTemplates.gradleProperties())
    PluginHelpers.writeFile(dir.resolve("README.md"),           PluginTemplates.readme(pluginName, groupId))

    // Esempio stage incluso di default
    val srcDir = dir.resolve(s"src/main/scala/${pkg.replace('.', '/')}")
    PluginHelpers.writeFile(srcDir.resolve("ExampleTransformStage.scala"),
                            PluginTemplates.transformStage(pkg, "ExampleTransform"))
    PluginHelpers.writeServiceFile(dir, "eu.webrobot.plugin.sdk.WTransformStage",
                                   Seq(s"$pkg.ExampleTransformStage"))

    println(
      s"""
         |✓ Plugin '$pluginName' creato in $dir
         |
         |Struttura generata:
         |  build.gradle.kts          ← dipende da webrobot-plugin-sdk
         |  src/main/scala/$pkg/
         |    ExampleTransformStage.scala  ← estende WTransformStage
         |  src/main/resources/META-INF/services/
         |    eu.webrobot.plugin.sdk.WTransformStage
         |
         |Prossimi passi:
         |  cd $dir
         |  ./gradlew jar
         |  webrobot plugin add stage <NomeStage> --type transform
         |  webrobot plugin add resolver <NomeResolver>
         |""".stripMargin)
  }

  private def createDirs(base: Path, pkg: String): Unit = {
    val pkgPath = pkg.replace('.', '/')
    Seq(
      s"src/main/scala/$pkgPath",
      "src/main/resources/META-INF/services",
      s"src/test/scala/$pkgPath"
    ).foreach(rel => Files.createDirectories(base.resolve(rel)))
  }
}

// ──────────────────────────────────────────────────────────────────────────────
// webrobot plugin add  (root of add subgroup)
// ──────────────────────────────────────────────────────────────────────────────

@Command(
  name        = "add",
  description = Array("Aggiunge un componente (stage, action, resolver) al plugin corrente."),
  subcommands = Array(
    classOf[PluginAddStageCommand],
    classOf[PluginAddResolverCommand],
    classOf[PluginAddActionCommand]
  ),
  mixinStandardHelpOptions = true
)
class PluginAddCommand extends Runnable {
  override def run(): Unit = ()
}

// ──────────────────────────────────────────────────────────────────────────────
// webrobot plugin add stage <Name> --type transform|filter|aggregate
// ──────────────────────────────────────────────────────────────────────────────

@Command(
  name        = "stage",
  description = Array("Aggiunge uno stage ETL al plugin (transform, filter o aggregate)."),
  mixinStandardHelpOptions = true
)
class PluginAddStageCommand extends Runnable {

  @Parameters(index = "0", description = Array("Nome della classe stage (es. UpperCase)"))
  private var stageName: String = ""

  @Option(names = Array("-t", "--type"),
          description = Array("Tipo: transform (default) | filter | aggregate"))
  private var stageType: String = "transform"

  @Option(names = Array("-d", "--dir"), description = Array("Root del progetto plugin (default: .)"))
  private var projectDir: String = "."

  override def run(): Unit = {
    val root   = Paths.get(projectDir)
    val pkg    = PluginHelpers.detectPackage(root)
    val srcDir = root.resolve(s"src/main/scala/${pkg.replace('.', '/')}")

    val (code, serviceIface) = stageType.toLowerCase match {
      case "filter"    =>
        (PluginTemplates.filterStage(pkg, stageName),
         "eu.webrobot.plugin.sdk.WFilterStage")
      case "aggregate" =>
        (PluginTemplates.aggregateStage(pkg, stageName),
         "eu.webrobot.plugin.sdk.WAggregateStage")
      case _           =>
        (PluginTemplates.transformStage(pkg, stageName),
         "eu.webrobot.plugin.sdk.WTransformStage")
    }

    PluginHelpers.writeFile(srcDir.resolve(s"${stageName}Stage.scala"), code)
    PluginHelpers.appendServiceFile(root, serviceIface, s"$pkg.${stageName}Stage")

    println(s"✓ ${stageType.capitalize} stage '${stageName}Stage' aggiunto in $srcDir")
    println(s"  Registrato in META-INF/services/$serviceIface")
  }
}

// ──────────────────────────────────────────────────────────────────────────────
// webrobot plugin add resolver <Name>
// ──────────────────────────────────────────────────────────────────────────────

@Command(
  name        = "resolver",
  description = Array("Aggiunge un AttributeResolver al plugin."),
  mixinStandardHelpOptions = true
)
class PluginAddResolverCommand extends Runnable {

  @Parameters(index = "0", description = Array("Nome della classe resolver (es. Price)"))
  private var resolverName: String = ""

  @Option(names = Array("-d", "--dir"), description = Array("Root del progetto plugin (default: .)"))
  private var projectDir: String = "."

  override def run(): Unit = {
    val root   = Paths.get(projectDir)
    val pkg    = PluginHelpers.detectPackage(root)
    val srcDir = root.resolve(s"src/main/scala/${pkg.replace('.', '/')}")

    PluginHelpers.writeFile(srcDir.resolve(s"${resolverName}Resolver.scala"),
                            PluginTemplates.resolver(pkg, resolverName))
    PluginHelpers.appendServiceFile(root, "eu.webrobot.plugin.sdk.WResolver",
                                    s"$pkg.${resolverName}Resolver")

    println(s"✓ Resolver '${resolverName}Resolver' aggiunto in $srcDir")
  }
}

// ──────────────────────────────────────────────────────────────────────────────
// webrobot plugin add action <Name>
// ──────────────────────────────────────────────────────────────────────────────

@Command(
  name        = "action",
  description = Array(
    "Aggiunge un'Action browser al plugin.",
    "NOTA: il caricamento a caldo delle action non è ancora supportato dall'ETL."
  ),
  mixinStandardHelpOptions = true
)
class PluginAddActionCommand extends Runnable {

  @Parameters(index = "0", description = Array("Nome della classe action (es. ScrollToBottom)"))
  private var actionName: String = ""

  @Option(names = Array("-d", "--dir"), description = Array("Root del progetto plugin (default: .)"))
  private var projectDir: String = "."

  override def run(): Unit = {
    val root   = Paths.get(projectDir)
    val pkg    = PluginHelpers.detectPackage(root)
    val srcDir = root.resolve(s"src/main/scala/${pkg.replace('.', '/')}")

    PluginHelpers.writeFile(srcDir.resolve(s"${actionName}Action.scala"),
                            PluginTemplates.action(pkg, actionName))
    PluginHelpers.appendServiceFile(root, "eu.webrobot.plugin.sdk.WAction",
                                    s"$pkg.${actionName}Action")

    println(s"✓ Action '${actionName}Action' aggiunta in $srcDir")
    println("  ⚠  Il caricamento a caldo delle action richiede il riavvio dell'ETL.")
  }
}

// ──────────────────────────────────────────────────────────────────────────────
// Helpers condivisi
// ──────────────────────────────────────────────────────────────────────────────

private object PluginHelpers {

  def detectPackage(root: Path): String = {
    val build = root.resolve("build.gradle.kts")
    if (Files.exists(build)) {
      val lines = Source.fromFile(build.toFile).getLines().toSeq
      lines.find(_.trim.startsWith("group")).flatMap { line =>
        val m = """group\s*=\s*"([^"]+)"""".r.findFirstMatchIn(line)
        m.map(_.group(1))
      }.getOrElse("eu.webrobot.plugins.myplugin")
    } else "eu.webrobot.plugins.myplugin"
  }

  def writeFile(path: Path, content: String): Unit = {
    Files.createDirectories(path.getParent)
    val w = new PrintWriter(path.toFile)
    try w.write(content) finally w.close()
  }

  def writeServiceFile(root: Path, iface: String, impls: Seq[String]): Unit = {
    val f = root.resolve(s"src/main/resources/META-INF/services/$iface")
    writeFile(f, impls.mkString("\n") + "\n")
  }

  def appendServiceFile(root: Path, iface: String, impl: String): Unit = {
    val f = root.resolve(s"src/main/resources/META-INF/services/$iface")
    Files.createDirectories(f.getParent)
    val existing = if (Files.exists(f)) new String(Files.readAllBytes(f)) else ""
    if (!existing.contains(impl)) {
      val w = new PrintWriter(new java.io.FileWriter(f.toFile, true))
      try w.write(impl + "\n") finally w.close()
    }
  }
}

// ──────────────────────────────────────────────────────────────────────────────
// Code templates
// ──────────────────────────────────────────────────────────────────────────────

private object PluginTemplates {

  def settings(pluginName: String): String =
    s"""rootProject.name = "$pluginName"
       |""".stripMargin

  def buildGradle(groupId: String, pluginName: String, sdkVersion: String): String =
    s"""plugins {
       |    id("scala")
       |    id("java-library")
       |}
       |
       |group   = "$groupId"
       |version = "0.1.0"
       |
       |repositories {
       |    mavenCentral()
       |    maven {
       |        name = "GitHubPackages"
       |        url  = uri("https://maven.pkg.github.com/WebRobot-Ltd/webrobot-etl")
       |        credentials {
       |            username = System.getenv("GITHUB_ACTOR") ?: "webroboteu"
       |            password = System.getenv("GITHUB_TOKEN") ?: ""
       |        }
       |    }
       |}
       |
       |val scalaV = "2.12"
       |
       |dependencies {
       |    // Only SDK dependency needed — no ETL internals required
       |    compileOnly("eu.webrobot:webrobot-plugin-sdk:$sdkVersion")
       |    compileOnly("org.scala-lang:scala-library:2.12.18")
       |
       |    testImplementation("org.scalatest:scalatest_$$scalaV:3.2.18")
       |    testImplementation("junit:junit:4.13.2")
       |}
       |
       |tasks.withType<Jar> {
       |    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
       |}
       |""".stripMargin

  def gradleProperties(): String =
    """org.gradle.jvmargs=-Xmx2g
      |""".stripMargin

  def readme(pluginName: String, groupId: String): String =
    s"""# $pluginName
       |
       |WebRobot ETL plugin built with the [WebRobot Plugin SDK](https://github.com/WebRobot-Ltd/webrobot-etl).
       |
       |## Build
       |
       |```bash
       |GITHUB_TOKEN=<your-token> ./gradlew jar
       |```
       |
       |## Add stages
       |
       |```bash
       |webrobot plugin add stage MyTransform --type transform
       |webrobot plugin add stage MyFilter    --type filter
       |webrobot plugin add stage MyAggregate --type aggregate
       |webrobot plugin add resolver MyPrice
       |webrobot plugin add action   MyClick
       |```
       |
       |## Deploy
       |
       |Copy the built JAR to the ETL plugins directory or configure the deployment path.
       |""".stripMargin

  def transformStage(pkg: String, name: String): String =
    s"""package $pkg
       |
       |import eu.webrobot.plugin.sdk.{WArgs, WRow, WTransformStage}
       |
       |class ${name}Stage extends WTransformStage {
       |
       |  override def name: String = "${camel2snake(name)}"
       |
       |  override def transform(row: WRow, args: WArgs): WRow = {
       |    // args.string(0) → primo argomento posizionale
       |    // row.str("fieldName") → legge un campo come String
       |    // row.set("output", value) → aggiunge/sovrascrive un campo
       |    val input = args.string(0, "text")
       |    row.str(input).fold(row)(value => row.set(input, value.toUpperCase))
       |  }
       |}
       |""".stripMargin

  def filterStage(pkg: String, name: String): String =
    s"""package $pkg
       |
       |import eu.webrobot.plugin.sdk.{WArgs, WRow, WFilterStage}
       |
       |class ${name}Stage extends WFilterStage {
       |
       |  override def name: String = "${camel2snake(name)}"
       |
       |  override def include(row: WRow, args: WArgs): Boolean = {
       |    // Restituisce true per tenere la riga, false per scartarla
       |    val field    = args.string(0, "value")
       |    val minValue = args.double(1, 0.0)
       |    row.double(field).exists(_ >= minValue)
       |  }
       |}
       |""".stripMargin

  def aggregateStage(pkg: String, name: String): String =
    s"""package $pkg
       |
       |import eu.webrobot.plugin.sdk.{WArgs, WRow, WAggregateStage}
       |
       |class ${name}Stage extends WAggregateStage {
       |
       |  override def name: String = "${camel2snake(name)}"
       |
       |  override def groupBy(row: WRow): String =
       |    // Chiave di raggruppamento — il motore ETL raggruppa le righe per questo valore
       |    row.str("category").getOrElse("")
       |
       |  override def combine(left: WRow, right: WRow, args: WArgs): WRow = {
       |    // Combina due righe dello stesso gruppo (come reduceByKey)
       |    val field = args.string(0, "amount")
       |    val sum   = left.double(field).getOrElse(0.0) + right.double(field).getOrElse(0.0)
       |    left.set(field, sum)
       |  }
       |}
       |""".stripMargin

  def resolver(pkg: String, name: String): String = {
    val tq = "\"\"\""
    s"""package $pkg
       |
       |import eu.webrobot.plugin.sdk.WResolver
       |
       |class ${name}Resolver extends WResolver {
       |
       |  override def name: String = "${camel2snake(name)}"
       |
       |  // Riceve il testo dell'elemento HTML e restituisce il valore estratto, se presente
       |  override def extract(text: String): Option[String] = {
       |    val pattern = ${tq}([0-9]+(?:[.,][0-9]{1,2})?)${tq}.r
       |    pattern.findFirstIn(text).map(_.replace(',', '.'))
       |  }
       |}
       |""".stripMargin
  }

  def action(pkg: String, name: String): String =
    s"""package $pkg
       |
       |import eu.webrobot.plugin.sdk.{ActionSpec, WAction, WActionArgs}
       |
       |// NOTE: hot-loading delle action non è ancora supportato dall'ETL.
       |// Questa classe va inclusa nel JAR e l'ETL deve essere riavviato.
       |class ${name}Action extends WAction {
       |
       |  override def name: String = "${camel2snake(name)}"
       |
       |  override def build(args: WActionArgs): ActionSpec = {
       |    val ms = args.int("ms", 500)
       |    ActionSpec(actionType = "sleep", params = Map("ms" -> ms))
       |  }
       |}
       |""".stripMargin

  private def camel2snake(s: String): String =
    s.replaceAll("([A-Z])", "_$1").toLowerCase.stripPrefix("_")
}
