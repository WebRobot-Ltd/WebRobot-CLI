package org.webrobot.cli.commands

import com.fasterxml.jackson.databind.JsonNode
import picocli.CommandLine.{Command, Option => Opt, Parameters}

import java.io.{ByteArrayOutputStream, File}
import java.net.{HttpURLConnection, URL}
import java.nio.file.{Files, Path, Paths}

// ── root command ──────────────────────────────────────────────────────────────

/**
 * webrobot bundle — partner-facing operations on plugin bundles.
 *
 * The bundle format (one ZIP containing api/, etl/, cli/, ui/dist/ +
 * a root manifest.json) and the platform `POST /admin/bundles/install`
 * endpoint are documented in
 * webrobot-elt-clouddashboard/docs/BUNDLE_DISTRIBUTION_FORMAT.md.
 *
 * Subcommands:
 *   webrobot bundle package [<repo-path>]      — runs scripts/package-bundle.sh in the plugin repo
 *   webrobot bundle upload  <bundle.zip>       — multipart POST to the platform
 */
@Command(
  name = "bundle",
  mixinStandardHelpOptions = true,
  description = Array("Plugin bundle: package locally and upload to the platform."),
  subcommands = Array(
    classOf[BundlePackageCommand],
    classOf[BundleUploadCommand]
  )
)
class RunBundleCommand extends BaseSubCommand {
  override def run(): Unit = new picocli.CommandLine(this).usage(System.out)
}

// ── shared base ───────────────────────────────────────────────────────────────

abstract class BundleBaseCommand extends BaseSubCommand {
  protected def baseUrl(): String = {
    val b = apiClient().getBasePath
    if (b.endsWith("/")) b.dropRight(1) else b
  }

  protected def addAuthHeaders(conn: HttpURLConnection): Unit = {
    val ah = apiClient().getApiKey
    if (ah != null && ah.nonEmpty) conn.setRequestProperty("Authorization", s"Bearer $ah")
    val custom = apiClient().getDefaultHeaderMap
    if (custom != null) {
      val it = custom.entrySet().iterator()
      while (it.hasNext) {
        val e = it.next()
        conn.setRequestProperty(e.getKey, e.getValue)
      }
    }
  }
}

// ── bundle package ────────────────────────────────────────────────────────────

@Command(
  name = "package",
  mixinStandardHelpOptions = true,
  description = Array(
    "Build the unified plugin bundle ZIP by running the repo's scripts/package-bundle.sh.",
    "The script must exist in the plugin repository — see the reference",
    "implementation in webrobot-sentimental-plugin."
  )
)
class BundlePackageCommand extends BaseSubCommand {

  @Parameters(
    index = "0", arity = "0..1",
    description = Array("Plugin repo path (default: current directory).")
  )
  var repoPath: String = "."

  @Opt(
    names = Array("--skip-build"),
    description = Array("Pass --skip-build to the package script (reuse existing artefacts).")
  )
  var skipBuild: Boolean = false

  override def run(): Unit = {
    val dir = Paths.get(repoPath).toAbsolutePath.normalize()
    if (!Files.isDirectory(dir)) {
      System.err.println(s"✗ not a directory: $dir")
      System.exit(2)
    }
    val script = dir.resolve("scripts/package-bundle.sh")
    if (!Files.isExecutable(script)) {
      System.err.println(s"✗ scripts/package-bundle.sh not found or not executable in $dir")
      System.err.println("  see: webrobot-elt-clouddashboard/docs/BUNDLE_DISTRIBUTION_FORMAT.md")
      System.exit(2)
    }

    val cmd = new java.util.ArrayList[String]()
    cmd.add(script.toString)
    if (skipBuild) cmd.add("--skip-build")

    val pb = new ProcessBuilder(cmd).directory(dir.toFile).inheritIO()
    val proc = pb.start()
    val rc = proc.waitFor()
    if (rc != 0) {
      System.err.println(s"✗ package-bundle.sh exited with code $rc")
      System.exit(rc)
    }
  }
}

// ── bundle upload ─────────────────────────────────────────────────────────────

@Command(
  name = "upload",
  mixinStandardHelpOptions = true,
  description = Array(
    "Upload a plugin bundle ZIP to the platform's aggregate-install endpoint.",
    "POSTs multipart/form-data to /webrobot/api/admin/bundles/install."
  )
)
class BundleUploadCommand extends BundleBaseCommand {

  @Parameters(
    index = "0",
    description = Array("Path to the <pluginId>-<version>.zip produced by `bundle package`.")
  )
  var bundleFile: File = _

  @Opt(
    names = Array("--build-type", "-b"),
    description = Array("Build type tag stored on each plugin_installations row (default: development).")
  )
  var buildType: String = "development"

  @Opt(
    names = Array("--force", "-f"),
    description = Array("Overwrite an existing (pluginId, version) bundle row.")
  )
  var force: Boolean = false

  override def run(): Unit = {
    init()
    if (bundleFile == null || !bundleFile.exists()) {
      System.err.println(s"✗ bundle file not found: $bundleFile")
      System.exit(2)
    }
    val bytes = Files.readAllBytes(bundleFile.toPath)
    println(s"→ Uploading ${bundleFile.getName} (${bytes.length} bytes) to ${baseUrl()}")
    printNode(uploadMultipart(bundleFile.getName, bytes))
  }

  private def uploadMultipart(filename: String, data: Array[Byte]): JsonNode = {
    val boundary = "----WebRobotCli" + System.currentTimeMillis()
    val buf = new ByteArrayOutputStream()

    def writePart(name: String, value: String): Unit = {
      buf.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"" + name + "\"\r\n\r\n").getBytes("UTF-8"))
      buf.write(value.getBytes("UTF-8"))
      buf.write("\r\n".getBytes("UTF-8"))
    }

    def writeFile(name: String, fname: String, ct: String, bytes: Array[Byte]): Unit = {
      buf.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"" + name + "\"; filename=\"" + fname + "\"\r\nContent-Type: " + ct + "\r\n\r\n").getBytes("UTF-8"))
      buf.write(bytes)
      buf.write("\r\n".getBytes("UTF-8"))
    }

    writeFile("bundle", filename, "application/zip", data)
    writePart("buildType", buildType)
    writePart("force", String.valueOf(force))
    buf.write(s"--$boundary--\r\n".getBytes("UTF-8"))

    val urlStr = s"${baseUrl()}/webrobot/api/admin/bundles/install"
    val conn = new URL(urlStr).openConnection().asInstanceOf[HttpURLConnection]
    conn.setDoOutput(true)
    conn.setRequestMethod("POST")
    conn.setRequestProperty("Content-Type", s"multipart/form-data; boundary=$boundary")
    conn.setConnectTimeout(30000)
    conn.setReadTimeout(120000)
    addAuthHeaders(conn)
    conn.getOutputStream.write(buf.toByteArray)
    conn.getOutputStream.close()

    val code = conn.getResponseCode
    val resp = new String(
      (if (code < 400) conn.getInputStream else conn.getErrorStream).readAllBytes(), "UTF-8")
    if (code >= 400) {
      System.err.println(s"✗ HTTP $code")
    }
    apiClient().getObjectMapper.readTree(resp)
  }
}
