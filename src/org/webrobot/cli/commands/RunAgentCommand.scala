package org.webrobot.cli.commands

import eu.webrobot.openapi.client.model.AgentDto
import org.webrobot.cli.openapi.{JsonCliUtil, OpenApiHttp}
import org.webrobot.cli.utils.DataGrid
import picocli.CommandLine.{Command, Option}

import scala.io.Source

@Command(
  name = "delete",
  sortOptions = false,
  description = Array("Elimina agent (DELETE /webrobot/api/agents/{agentId})."),
  footer = Array()
)
class RunDeleteAgentCommand extends BaseSubCommand {

  @Option(names = Array("-i", "--id"), description = Array("agent id"), required = true)
  private var agentId: String = ""

  override def startRun(): Unit = {
    this.init()
    val path = "/webrobot/api/agents/" + apiClient().escapeString(agentId)
    OpenApiHttp.deleteJson(apiClient(), path)
  }
}

@Command(
  name = "getfromname",
  sortOptions = false,
  description = Array("Agent per nome (GET .../agents/{categoryId}/name/{agentName}). Usare -c/--categoryId (o alias -p/--projectId)."),
  footer = Array()
)
class GetAgentFromNameCommand extends BaseSubCommand {

  @Option(
    names = Array("-c", "--categoryId", "-p", "--projectId"),
    description = Array("category id (alias storico: -p/--projectId)"),
    required = true
  )
  private var categoryId: String = ""

  @Option(names = Array("-n", "--name"), description = Array("agent name"), required = true)
  private var agentName: String = ""

  override def startRun(): Unit = {
    this.init()
    val path = "/webrobot/api/agents/" + apiClient().escapeString(categoryId) + "/name/" + apiClient().escapeString(
      agentName
    )
    val node = OpenApiHttp.getJson(apiClient(), path)
    val dg = new DataGrid("Id", "Name", "Description", "Code", "CategoryId", "CreatedAt")
    dg.add(
      JsonCliUtil.text(node, "id"),
      JsonCliUtil.text(node, "name"),
      JsonCliUtil.text(node, "description"),
      JsonCliUtil.text(node, "code"),
      JsonCliUtil.text(node, "categoryId"),
      JsonCliUtil.text(node, "createdAt")
    )
    if (dg.size > 0) {
      dg.render
      System.out.println(dg.size + " rows in set\n")
    } else System.out.println("Empty set\n")
  }
}

@Command(
  name = "get",
  sortOptions = false,
  description = Array("Agent per id (GET .../agents/{categoryId}/{agentId})."),
  footer = Array()
)
class GetAgentFromIdCommand extends BaseSubCommand {

  @Option(
    names = Array("-c", "--categoryId", "-p", "--projectId"),
    description = Array("category id (alias storico: -p/--projectId)"),
    required = true
  )
  private var categoryId: String = ""

  @Option(names = Array("-i", "--id"), description = Array("agent id"), required = true)
  private var agentId: String = ""

  override def startRun(): Unit = {
    this.init()
    val path =
      "/webrobot/api/agents/" + apiClient().escapeString(categoryId) + "/" + apiClient().escapeString(agentId)
    val node = OpenApiHttp.getJson(apiClient(), path)
    val dg = new DataGrid("Id", "Name", "Description", "Code", "CategoryId", "CreatedAt")
    dg.add(
      JsonCliUtil.text(node, "id"),
      JsonCliUtil.text(node, "name"),
      JsonCliUtil.text(node, "description"),
      JsonCliUtil.text(node, "code"),
      JsonCliUtil.text(node, "categoryId"),
      JsonCliUtil.text(node, "createdAt")
    )
    if (dg.size > 0) {
      dg.render
      System.out.println(dg.size + " rows in set\n")
    } else System.out.println("Empty set\n")
  }
}

@Command(
  name = "list",
  sortOptions = false,
  description = Array("Elenco agent per category (GET /webrobot/api/agents/{categoryId})."),
  footer = Array()
)
class RunListAgentCommand extends BaseSubCommand {

  @Option(
    names = Array("-c", "--categoryId", "-p", "--projectId"),
    description = Array("category id (alias storico: -p/--projectId)"),
    required = true
  )
  private var categoryId: String = ""

  override def startRun(): Unit = {
    this.init()
    val path = "/webrobot/api/agents/" + apiClient().escapeString(categoryId)
    val node = OpenApiHttp.getJson(apiClient(), path)
    if (node != null && node.isArray)
      JsonCliUtil.renderArrayGrid(node, "id", "name", "description", "categoryId", "createdAt")
    else JsonCliUtil.printJson(node)
  }
}

@Command(
  name = "update",
  sortOptions = false,
  description = Array("Aggiorna agent (PUT .../agents/{categoryId}/{agentId})."),
  footer = Array()
)
class RunUpdateAgentCommand extends BaseSubCommand {

  @Option(
    names = Array("-c", "--categoryId", "-p", "--projectId"),
    description = Array("category id (alias storico: -p/--projectId)"),
    required = true
  )
  private var categoryId: String = ""

  @Option(names = Array("-i", "--id"), description = Array("agent id"), required = true)
  private var agentId: String = ""

  @Option(names = Array("-n", "--name"), description = Array("nome"), required = true)
  private var agentName: String = ""

  @Option(names = Array("-d", "--description"), description = Array("descrizione"), required = true)
  private var agentDescription: String = ""

  @Option(names = Array("-f", "--codeFile"), description = Array("file codice (opzionale)"))
  private var codeFile: String = ""

  override def startRun(): Unit = {
    this.init()
    val dto = new AgentDto()
    dto.setId(agentId)
    dto.setName(agentName)
    dto.setDescription(agentDescription)
    dto.setCategoryId(categoryId)
    if (codeFile != null && codeFile.nonEmpty) {
      var code = ""
      for (line <- Source.fromFile(codeFile).getLines()) {
        code = code + "\r\n" + line
      }
      dto.setCode(code)
    }
    val path =
      "/webrobot/api/agents/" + apiClient().escapeString(categoryId) + "/" + apiClient().escapeString(agentId)
    OpenApiHttp.putJson(apiClient(), path, dto)
  }
}

@Command(
  name = "add",
  sortOptions = false,
  description = Array("Crea agent (POST /webrobot/api/agents). Impostare categoryId con -c (o -p)."),
  footer = Array(),
  subcommands = Array()
)
class RunAddAgentCommand extends BaseSubCommand {

  @Option(names = Array("-n", "--name"), description = Array("nome"), required = true)
  private var agentName: String = ""

  @Option(names = Array("-d", "--description"), description = Array("descrizione"), required = true)
  private var agentDescription: String = ""

  @Option(names = Array("-f", "--codeFile"), description = Array("file codice (opzionale)"))
  private var codeFile: String = ""

  @Option(
    names = Array("-c", "--categoryId", "-p", "--projectId"),
    description = Array("category id (alias storico: -p/--projectId)"),
    required = true
  )
  private var categoryId: String = ""

  override def startRun(): Unit = {
    this.init()
    val dto = new AgentDto()
    dto.setName(agentName)
    dto.setDescription(agentDescription)
    dto.setCategoryId(categoryId)
    if (codeFile != null && codeFile.nonEmpty) {
      var code = ""
      for (line <- Source.fromFile(codeFile).getLines()) {
        code = code + "\r\n" + line
      }
      dto.setCode(code)
    }
    val node = OpenApiHttp.postJson(apiClient(), "/webrobot/api/agents", dto)
    JsonCliUtil.printJson(node)
  }
}

@Command(
  name = "agent",
  mixinStandardHelpOptions = true,
  sortOptions = false,
  description = Array("Agent (REST /webrobot/api/agents/...). categoryId: preferire -c/--categoryId; -p/--projectId resta alias compatibile."),
  footer = Array(),
  subcommands = Array(
    classOf[GetAgentFromNameCommand],
    classOf[GetAgentFromIdCommand],
    classOf[RunAddAgentCommand],
    classOf[RunUpdateAgentCommand],
    classOf[RunListAgentCommand],
    classOf[RunDeleteAgentCommand]
  )
)
class RunAgentCommand extends Runnable {

  def run(): Unit = {
    System.err.println(
      "Uso: webrobot agent <sottocomando>. Sottocomandi: add | update | list | delete | get | getfromname"
    )
    System.err.println("Esempio: webrobot agent list -c <categoryId>")
  }
}
