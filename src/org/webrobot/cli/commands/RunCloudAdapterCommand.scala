package org.webrobot.cli.commands

import com.fasterxml.jackson.databind.{JsonNode, ObjectMapper}
import picocli.CommandLine.{Command, Option => Opt, Parameters}

import java.io.ByteArrayOutputStream
import java.net.{HttpURLConnection, URL}

/**
 * webrobot cloud-adapter — partner-facing operations on cloud-provider VM adapters.
 *
 * Subcommands:
 *   webrobot cloud-adapter list                    list MY adapters (scoped to caller's org)
 *   webrobot cloud-adapter list --all              list ALL adapters (super_admin only)
 *   webrobot cloud-adapter status <providerKey>    one adapter's health + config
 *
 * The actual upload of a new adapter version uses the standard bundle path:
 *   webrobot bundle package [<repo>]               build dist/<id>-<v>.zip
 *   webrobot bundle upload <zip>                   multipart POST to the platform
 *
 * For the partner workflow (fork the reference plugin, implement the role,
 * package, upload) see:
 *   https://github.com/WebRobot-Ltd/webrobot-scaleway-ansible-plugin
 */
@Command(
  name = "cloud-adapter",
  mixinStandardHelpOptions = true,
  description = Array("Partner ops on cloud-provider VM adapters (list / status). Upload uses `webrobot bundle`."),
  subcommands = Array(
    classOf[CloudAdapterListCommand],
    classOf[CloudAdapterStatusCommand]
  )
)
class RunCloudAdapterCommand extends BaseSubCommand {
  override def run(): Unit = new picocli.CommandLine(this).usage(System.out)
}

abstract class CloudAdapterBaseCommand extends BaseSubCommand {
  protected val MAPPER = new ObjectMapper()

  protected def baseUrl(): String = {
    val b = apiClient().getBasePath
    if (b.endsWith("/")) b.dropRight(1) else b
  }

  protected def addAuth(conn: HttpURLConnection): Unit = {
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

  protected def httpGetJson(path: String): JsonNode = {
    val url = new URL(baseUrl() + path)
    val conn = url.openConnection().asInstanceOf[HttpURLConnection]
    conn.setRequestMethod("GET")
    conn.setConnectTimeout(15000)
    conn.setReadTimeout(60000)
    conn.setRequestProperty("Accept", "application/json")
    addAuth(conn)
    val sc = conn.getResponseCode
    val stream = if (sc / 100 == 2) conn.getInputStream else conn.getErrorStream
    val baos = new ByteArrayOutputStream()
    if (stream != null) {
      val buf = new Array[Byte](8192)
      var n = stream.read(buf)
      while (n >= 0) { baos.write(buf, 0, n); n = stream.read(buf) }
      stream.close()
    }
    conn.disconnect()
    val body = baos.toString("UTF-8")
    if (sc / 100 != 2) {
      sys.error(s"HTTP $sc on $path — ${body.take(500)}")
    }
    MAPPER.readTree(body)
  }
}

// ── list ──────────────────────────────────────────────────────────────────────

@Command(
  name = "list",
  mixinStandardHelpOptions = true,
  description = Array(
    "List cloud-provider adapters. Default: only adapters owned by the caller's org.",
    "Use --all to list every registered adapter (built-in + bundle, super_admin only)."
  )
)
class CloudAdapterListCommand extends CloudAdapterBaseCommand {

  @Opt(
    names = Array("--all"),
    description = Array("List every registered adapter (super_admin only).")
  )
  var listAll: Boolean = false

  override def startRun(): Unit = {
    init()
    val path =
      if (listAll) "/webrobot/api/admin/cloud-adapters"
      else "/webrobot/api/admin/cloud-adapters/mine"
    val node = httpGetJson(path)
    val data = node.path("data")
    val n = if (data.isArray) data.size() else 0
    if (n == 0) {
      System.out.println(if (listAll) "No adapters registered." else "You haven't published any adapter yet.")
      System.out.println("\nTo publish one:")
      System.out.println("  1) Fork https://github.com/WebRobot-Ltd/webrobot-scaleway-ansible-plugin")
      System.out.println("  2) Implement ansible/roles/<provider>_adapter/")
      System.out.println("  3) ./scripts/package-bundle.sh")
      System.out.println("  4) webrobot bundle upload dist/<pluginId>-<version>.zip")
      return
    }
    System.out.printf("%-20s %-12s %-7s %-9s %-7s %-12s %s%n",
      "PROVIDER_KEY", "VERSION", "API", "BUILTIN", "ENABLED", "HEALTH", "ROLE")
    val it = data.elements()
    while (it.hasNext) {
      val a = it.next()
      System.out.printf("%-20s %-12s %-7s %-9s %-7s %-12s %s%n",
        a.path("provider_key").asText("-"),
        a.path("version").asText("-"),
        a.path("api_version").asText("-"),
        if (a.path("builtin").asBoolean(false)) "yes" else "no",
        if (a.path("enabled").asBoolean(false)) "yes" else "no",
        a.path("health_status").asText("unknown"),
        a.path("ansible_role").asText("-")
      )
    }
    System.out.println(s"\n$n adapter(s).")
  }
}

// ── status ────────────────────────────────────────────────────────────────────

@Command(
  name = "status",
  mixinStandardHelpOptions = true,
  description = Array("Show config + health of a single adapter.")
)
class CloudAdapterStatusCommand extends CloudAdapterBaseCommand {

  @Parameters(
    index = "0", arity = "1",
    description = Array("Provider key (e.g. hetzner, scaleway).")
  )
  var providerKey: String = ""

  override def startRun(): Unit = {
    init()
    if (providerKey == null || providerKey.isEmpty) {
      System.err.println("Missing providerKey. Usage: webrobot cloud-adapter status <providerKey>")
      sys.exit(2)
    }
    val all = httpGetJson("/webrobot/api/admin/cloud-adapters")
    val data = all.path("data")
    val it = if (data.isArray) data.elements() else java.util.Collections.emptyIterator[JsonNode]()
    var found: JsonNode = null
    while (it.hasNext && found == null) {
      val a = it.next()
      if (providerKey.equalsIgnoreCase(a.path("provider_key").asText(""))) found = a
    }
    if (found == null) {
      System.err.println(s"No adapter found with provider_key=$providerKey")
      sys.exit(3)
    }
    System.out.println(s"Provider:        ${found.path("provider_key").asText("-")}")
    System.out.println(s"Plugin id:       ${found.path("plugin_id").asText("-")}")
    System.out.println(s"Version:         ${found.path("version").asText("-")}")
    System.out.println(s"API version:     ${found.path("api_version").asText("-")}")
    System.out.println(s"Ansible role:    ${found.path("ansible_role").asText("-")}")
    System.out.println(s"Built-in:        ${found.path("builtin").asBoolean(false)}")
    System.out.println(s"Enabled:         ${found.path("enabled").asBoolean(false)}")
    System.out.println(s"Health status:   ${found.path("health_status").asText("unknown")}")
    System.out.println(s"Last health:     ${found.path("last_health_check_at").asText("never")}")
    System.out.println(s"Failures (cons): ${found.path("consecutive_failures").asInt(0)}")
    if (found.path("regions").isArray) {
      val arr = found.path("regions")
      val sb = new StringBuilder()
      val rit = arr.elements()
      while (rit.hasNext) {
        if (sb.nonEmpty) sb.append(", ")
        sb.append(rit.next().asText(""))
      }
      System.out.println(s"Regions:         $sb")
    }
    if (found.path("supported_actions").isArray) {
      val arr = found.path("supported_actions")
      val sb = new StringBuilder()
      val rit = arr.elements()
      while (rit.hasNext) {
        if (sb.nonEmpty) sb.append(", ")
        sb.append(rit.next().asText(""))
      }
      System.out.println(s"Actions:         $sb")
    }
    if (found.path("compliance_flags").isArray && found.path("compliance_flags").size() > 0) {
      val arr = found.path("compliance_flags")
      val sb = new StringBuilder()
      val rit = arr.elements()
      while (rit.hasNext) {
        if (sb.nonEmpty) sb.append(", ")
        sb.append(rit.next().asText(""))
      }
      System.out.println(s"Compliance:      $sb")
    }
    val bundleId = found.path("bundle_id")
    if (!bundleId.isNull && !bundleId.isMissingNode) {
      System.out.println(s"Bundle id:       ${bundleId.asLong(0)}")
    }
  }
}
