package org.webrobot.cli.commands

import eu.webrobot.openapi.client.model.JobCategoryDto
import org.webrobot.cli.openapi.{JsonCliUtil, OpenApiHttp}
import picocli.CommandLine.{Command, Option}

@Command(
  name = "list",
  sortOptions = false,
  description = Array("Elenco categorie job (GET /webrobot/api/categories)."),
  footer = Array()
)
class RunListCategoryCommand extends BaseSubCommand {

  override def startRun(): Unit = {
    this.init()
    val node = OpenApiHttp.getJson(apiClient(), "/webrobot/api/categories")
    if (node != null && node.isArray)
      JsonCliUtil.renderArrayGrid(node, "id", "name", "description", "enabled", "createdAt", "updatedAt")
    else JsonCliUtil.printJson(node)
  }
}

@Command(
  name = "get",
  sortOptions = false,
  description = Array("Categoria per id (GET /webrobot/api/categories/id/{categoryId})."),
  footer = Array()
)
class RunGetCategoryCommand extends BaseSubCommand {

  @Option(names = Array("-i", "--categoryId"), description = Array("category id"), required = true)
  private var categoryId: String = ""

  override def startRun(): Unit = {
    this.init()
    val path = "/webrobot/api/categories/id/" + apiClient().escapeString(categoryId)
    val node = OpenApiHttp.getJson(apiClient(), path)
    JsonCliUtil.printJson(node)
  }
}

@Command(
  name = "getbyname",
  sortOptions = false,
  description = Array("Categoria per nome (GET /webrobot/api/categories/{categoryName})."),
  footer = Array()
)
class RunGetCategoryByNameCommand extends BaseSubCommand {

  @Option(names = Array("-n", "--name"), description = Array("nome categoria"), required = true)
  private var categoryName: String = ""

  override def startRun(): Unit = {
    this.init()
    val path = "/webrobot/api/categories/" + apiClient().escapeString(categoryName)
    val node = OpenApiHttp.getJson(apiClient(), path)
    JsonCliUtil.printJson(node)
  }
}

@Command(
  name = "add",
  sortOptions = false,
  description = Array("Crea categoria (POST /webrobot/api/categories)."),
  footer = Array()
)
class RunAddCategoryCommand extends BaseSubCommand {

  @Option(names = Array("-n", "--name"), description = Array("nome"), required = true)
  private var name: String = ""

  @Option(names = Array("-d", "--description"), description = Array("descrizione"))
  private var description: String = ""

  @Option(names = Array("--icon"), description = Array("icona"))
  private var icon: String = ""

  @Option(names = Array("-e", "--enabled"), description = Array("true|false (default true)"))
  private var enabledStr: String = "true"

  override def startRun(): Unit = {
    this.init()
    val dto = new JobCategoryDto()
    dto.setName(name)
    if (description != null) dto.setDescription(description)
    if (icon != null && icon.nonEmpty) dto.setIcon(icon)
    dto.setEnabled(java.lang.Boolean.parseBoolean(enabledStr))
    val node = OpenApiHttp.postJson(apiClient(), "/webrobot/api/categories", dto)
    JsonCliUtil.printJson(node)
  }
}

@Command(
  name = "update",
  sortOptions = false,
  description = Array("Aggiorna categoria (PUT /webrobot/api/categories/id/{categoryId})."),
  footer = Array()
)
class RunUpdateCategoryCommand extends BaseSubCommand {

  @Option(names = Array("-i", "--categoryId"), description = Array("category id"), required = true)
  private var categoryId: String = ""

  @Option(names = Array("-n", "--name"), description = Array("nome"), required = true)
  private var name: String = ""

  @Option(names = Array("-d", "--description"), description = Array("descrizione"))
  private var description: String = ""

  @Option(names = Array("--icon"), description = Array("icona"))
  private var icon: String = ""

  @Option(names = Array("-e", "--enabled"), description = Array("true|false (default true)"))
  private var enabledStr: String = "true"

  override def startRun(): Unit = {
    this.init()
    val dto = new JobCategoryDto()
    dto.setName(name)
    if (description != null) dto.setDescription(description)
    if (icon != null && icon.nonEmpty) dto.setIcon(icon)
    dto.setEnabled(java.lang.Boolean.parseBoolean(enabledStr))
    val path = "/webrobot/api/categories/id/" + apiClient().escapeString(categoryId)
    OpenApiHttp.putJson(apiClient(), path, dto)
  }
}

@Command(
  name = "delete",
  sortOptions = false,
  description = Array("Elimina categoria (DELETE /webrobot/api/categories/id/{categoryId})."),
  footer = Array()
)
class RunDeleteCategoryCommand extends BaseSubCommand {

  @Option(names = Array("-i", "--categoryId"), description = Array("category id"), required = true)
  private var categoryId: String = ""

  override def startRun(): Unit = {
    this.init()
    val path = "/webrobot/api/categories/id/" + apiClient().escapeString(categoryId)
    OpenApiHttp.deleteJson(apiClient(), path)
  }
}

@Command(
  name = "category",
  mixinStandardHelpOptions = true,
  sortOptions = false,
  description = Array(
    "Categorie job (REST /webrobot/api/categories/...). Distinte dal progetto: gli agent sono legati a una categoryId; i job vivono sotto un projectId."
  ),
  footer = Array(),
  subcommands = Array(
    classOf[RunListCategoryCommand],
    classOf[RunGetCategoryCommand],
    classOf[RunGetCategoryByNameCommand],
    classOf[RunAddCategoryCommand],
    classOf[RunUpdateCategoryCommand],
    classOf[RunDeleteCategoryCommand]
  )
)
class RunCategoryCommand extends Runnable {

  def run(): Unit = {
    System.err.println(
      "Uso: webrobot category <sottocomando>. Sottocomandi: list | get | getbyname | add | update | delete"
    )
    System.err.println("Esempio: webrobot category list")
  }
}
