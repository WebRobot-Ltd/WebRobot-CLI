package org.webrobot.cli.commands

import picocli.CommandLine.{Command, Parameters}

import java.io.File
import java.net.{HttpURLConnection, URL}
import java.nio.file.{Files, Path, Paths, StandardCopyOption}

/**
 * Install a CLI plugin JAR into ~/.webrobot/plugins/.
 *
 * Four input formats accepted:
 *   - local file:        webrobot cli plugins install ./target/foo.jar
 *   - HTTP(S) URL:       webrobot cli plugins install https://example.com/foo.jar
 *   - Maven coordinates: webrobot cli plugins install com.github.WebRobot-Ltd:webrobot-sentimental-plugin-cli:v0.1.0
 *   - WebRobot platform: webrobot cli plugins install webrobot:sentimental-plugin-cli[@<version>]
 *
 * Maven coordinates are resolved against JitPack first, then Maven Central.
 * The `webrobot:` scheme hits the platform's
 * GET /webrobot/api/admin/bundles/cli-plugins/<pluginId>?version=<v> endpoint
 * (Jersey side) which streams the CLI JAR shipped inside the latest approved
 * bundle for that pluginId. Auth headers are added if the CLI has been
 * configured with credentials (`webrobot config`).
 */
@Command(
  name = "install",
  description = Array("Install a CLI plugin JAR (local file, URL, Maven coords, or webrobot:<pluginId>).")
)
class CliPluginsInstall extends BaseSubCommand {

  @Parameters(paramLabel = "SOURCE",
              description = Array("local jar path, http(s) URL, groupId:artifactId:version, or webrobot:<pluginId>[@version]"))
  var source: String = _

  private val pluginsDir: Path = Paths.get(System.getProperty("user.home"), ".webrobot", "plugins")

  override def run(): Unit = {
    Files.createDirectories(pluginsDir)
    val target =
      if (source.startsWith("webrobot:"))                                 installFromPlatform(source.substring("webrobot:".length))
      else if (source.startsWith("http://") || source.startsWith("https://")) installFromUrl(source)
      else if (source.contains(":") && !new File(source).exists())        installFromCoordinates(source)
      else                                                                installFromFile(source)
    System.out.println(s"Installed: $target")
  }

  private def installFromPlatform(spec: String): Path = {
    // Accept `pluginId` or `pluginId@version`
    val (pluginId, versionOpt) = spec.split("@", 2) match {
      case Array(p, v) => (p, Some(v))
      case Array(p)    => (p, None)
    }
    init()  // wires apiClient() (auth headers + base URL) from BaseSubCommand
    val base = {
      val b = apiClient().getBasePath
      if (b.endsWith("/")) b.dropRight(1) else b
    }
    val urlStr = s"$base/webrobot/api/admin/bundles/cli-plugins/${java.net.URLEncoder.encode(pluginId, "UTF-8")}" +
      versionOpt.map(v => s"?version=${java.net.URLEncoder.encode(v, "UTF-8")}").getOrElse("")

    val conn = new URL(urlStr).openConnection().asInstanceOf[HttpURLConnection]
    conn.setRequestMethod("GET")
    conn.setInstanceFollowRedirects(true)
    conn.setConnectTimeout(15000)
    conn.setReadTimeout(120000)
    val ah = apiClient().getApiKey
    if (ah != null && ah.nonEmpty) conn.setRequestProperty("Authorization", s"Bearer $ah")

    val sc = conn.getResponseCode
    if (sc / 100 != 2) {
      val errMsg = try {
        new String(conn.getErrorStream.readAllBytes(), "UTF-8")
      } catch { case _: Throwable => "" }
      conn.disconnect()
      throw new RuntimeException(s"HTTP $sc on $urlStr — $errMsg")
    }
    val resolvedVersion = Option(conn.getHeaderField("X-Plugin-Version")).getOrElse(versionOpt.getOrElse("latest"))
    val name = s"$pluginId-$resolvedVersion.jar"
    val dst  = pluginsDir.resolve(name)
    val in   = conn.getInputStream
    try Files.copy(in, dst, StandardCopyOption.REPLACE_EXISTING)
    finally { in.close(); conn.disconnect() }
    dst
  }

  private def installFromFile(path: String): Path = {
    val src = Paths.get(path)
    if (!Files.exists(src)) sys.error(s"File not found: $path")
    val dst = pluginsDir.resolve(src.getFileName)
    Files.copy(src, dst, StandardCopyOption.REPLACE_EXISTING)
    dst
  }

  private def installFromUrl(url: String): Path = {
    val name = url.split("/").lastOption.filter(_.endsWith(".jar")).getOrElse("plugin.jar")
    val dst  = pluginsDir.resolve(name)
    download(url, dst)
    dst
  }

  private def installFromCoordinates(coords: String): Path = {
    val parts = coords.split(":")
    if (parts.length != 3) sys.error(s"Bad coordinates '$coords' — expected groupId:artifactId:version")
    val Array(group, artifact, version) = parts
    val groupPath = group.replace('.', '/')
    val name      = s"$artifact-$version.jar"

    val candidates = Seq(
      s"https://jitpack.io/$groupPath/$artifact/$version/$name",
      s"https://repo1.maven.org/maven2/$groupPath/$artifact/$version/$name"
    )

    val dst = pluginsDir.resolve(name)
    val ok = candidates.exists { url =>
      System.err.println(s"[webrobot] trying $url")
      try { download(url, dst); true } catch { case _: Throwable => false }
    }
    if (!ok) sys.error(s"Could not resolve $coords from JitPack or Maven Central")
    dst
  }

  private def download(url: String, dst: Path): Unit = {
    val conn = new URL(url).openConnection().asInstanceOf[HttpURLConnection]
    conn.setRequestMethod("GET")
    conn.setInstanceFollowRedirects(true)
    conn.setConnectTimeout(15000)
    conn.setReadTimeout(60000)
    val sc = conn.getResponseCode
    if (sc / 100 != 2) {
      conn.disconnect()
      throw new RuntimeException(s"HTTP $sc on $url")
    }
    val in = conn.getInputStream
    try Files.copy(in, dst, StandardCopyOption.REPLACE_EXISTING)
    finally { in.close(); conn.disconnect() }
  }
}
