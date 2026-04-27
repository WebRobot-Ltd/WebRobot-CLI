package org.webrobot.cli.commands

import org.webrobot.cli.openapi.{JsonCliUtil, OpenApiHttp}
import picocli.CommandLine.{Command, Option}

@Command(name = "plans-list", sortOptions = false,
  description = Array("Piani billing (GET /webrobot/api/billing/plans)."))
class RunListBillingPlansCommand extends BaseSubCommand {

  @Option(names = Array("--org-id"), description = Array("filtro organizationId"))
  private var organizationId: String = ""

  @Option(names = Array("--standard"), description = Array("filtro standard (true|false)"))
  private var standard: String = ""

  override def startRun(): Unit = {
    this.init()
    val tuples = Seq(
      scala.Option(organizationId).filter(_.nonEmpty).map("organizationId" -> _.asInstanceOf[AnyRef]),
      scala.Option(standard).filter(_.nonEmpty).map("standard" -> _.asInstanceOf[AnyRef])
    ).flatten
    val node =
      if (tuples.isEmpty) OpenApiHttp.getJson(apiClient(), "/webrobot/api/billing/plans")
      else OpenApiHttp.getJson(apiClient(), "/webrobot/api/billing/plans",
        OpenApiHttp.pairs(apiClient(), tuples: _*))
    if (node != null && node.isArray)
      JsonCliUtil.renderArrayGrid(node, "id", "name", "price", "currency", "organizationId", "active")
    else JsonCliUtil.printJson(node)
  }
}

@Command(name = "plans-add", sortOptions = false,
  description = Array("Crea piano billing (POST /webrobot/api/billing/plans). Usa --json per body completo."))
class RunAddBillingPlanCommand extends BaseSubCommand {

  @Option(names = Array("--json"), description = Array("body JSON piano"), required = true)
  private var jsonBody: String = ""

  override def startRun(): Unit = {
    this.init()
    val node = OpenApiHttp.postJson(apiClient(), "/webrobot/api/billing/plans",
      apiClient().getObjectMapper.readTree(jsonBody.trim))
    JsonCliUtil.printJson(node)
  }
}

@Command(name = "plans-update", sortOptions = false,
  description = Array("Aggiorna piano billing (PUT /webrobot/api/billing/plans/{id})."))
class RunUpdateBillingPlanCommand extends BaseSubCommand {

  @Option(names = Array("-i", "--id"), description = Array("plan id"), required = true)
  private var planId: String = ""

  @Option(names = Array("--json"), description = Array("body JSON piano"), required = true)
  private var jsonBody: String = ""

  override def startRun(): Unit = {
    this.init()
    val node = OpenApiHttp.putJson(apiClient(),
      "/webrobot/api/billing/plans/" + apiClient().escapeString(planId),
      apiClient().getObjectMapper.readTree(jsonBody.trim))
    JsonCliUtil.printJson(node)
  }
}

@Command(name = "plans-delete", sortOptions = false,
  description = Array("Elimina piano billing (DELETE /webrobot/api/billing/plans/{id})."))
class RunDeleteBillingPlanCommand extends BaseSubCommand {

  @Option(names = Array("-i", "--id"), description = Array("plan id"), required = true)
  private var planId: String = ""

  override def startRun(): Unit = {
    this.init()
    OpenApiHttp.deleteJson(apiClient(), "/webrobot/api/billing/plans/" + apiClient().escapeString(planId))
  }
}

@Command(name = "custom-plan", sortOptions = false,
  description = Array("Crea piano custom (POST /webrobot/api/billing/custom-plan). Usa --json per body."))
class RunCustomBillingPlanCommand extends BaseSubCommand {

  @Option(names = Array("--json"), description = Array("body JSON piano custom"), required = true)
  private var jsonBody: String = ""

  override def startRun(): Unit = {
    this.init()
    val node = OpenApiHttp.postJson(apiClient(), "/webrobot/api/billing/custom-plan",
      apiClient().getObjectMapper.readTree(jsonBody.trim))
    JsonCliUtil.printJson(node)
  }
}

@Command(name = "billing-refresh", sortOptions = false,
  description = Array("Refresh stato billing organizzazione (POST /webrobot/api/auth/organizations/billing/refresh)."))
class RunBillingRefreshCommand extends BaseSubCommand {

  override def startRun(): Unit = {
    this.init()
    val node = OpenApiHttp.postJson(apiClient(),
      "/webrobot/api/auth/organizations/billing/refresh",
      com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode())
    JsonCliUtil.printJson(node)
  }
}

@Command(
  name = "billing",
  mixinStandardHelpOptions = true,
  sortOptions = false,
  description = Array("Billing & piani (REST /webrobot/api/billing/...)."),
  subcommands = Array(
    classOf[RunListBillingPlansCommand],
    classOf[RunAddBillingPlanCommand],
    classOf[RunUpdateBillingPlanCommand],
    classOf[RunDeleteBillingPlanCommand],
    classOf[RunCustomBillingPlanCommand],
    classOf[RunBillingRefreshCommand]
  )
)
class RunBillingCommand extends Runnable {

  def run(): Unit = {
    System.err.println("Uso: webrobot billing <sottocomando>.")
    System.err.println("Sottocomandi: plans-list | plans-add | plans-update | plans-delete | custom-plan | billing-refresh")
  }
}
