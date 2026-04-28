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
  description = Array(
    "Elenco agent. Con -c: per categoryId. Con -p: risolve gli agent dai job del progetto."
  ),
  footer = Array()
)
class RunListAgentCommand extends BaseSubCommand {

  @Option(names = Array("-c", "--categoryId"), description = Array("category id"))
  private var categoryId: String = ""

  @Option(names = Array("-p", "--projectId"), description = Array("project id — risolve gli agent dai job del progetto"))
  private var projectId: String = ""

  override def startRun(): Unit = {
    this.init()
    if (projectId.nonEmpty) {
      listByProject()
    } else if (categoryId.nonEmpty) {
      val node = OpenApiHttp.getJson(apiClient(), "/webrobot/api/agents/" + apiClient().escapeString(categoryId))
      if (node != null && node.isArray)
        JsonCliUtil.renderArrayGrid(node, "id", "name", "description", "categoryId", "createdAt")
      else JsonCliUtil.printJson(node)
    } else {
      System.err.println("Specificare -c <categoryId> oppure -p <projectId>")
    }
  }

  private def listByProject(): Unit = {
    import scala.collection.JavaConverters._

    // 1. Fetch jobs for the project to collect unique agentIds
    val jobsNode = OpenApiHttp.getJson(apiClient(), "/webrobot/api/projects/id/" + apiClient().escapeString(projectId) + "/jobs")
    if (jobsNode == null || !jobsNode.isArray) {
      System.err.println("Nessun job trovato per il progetto " + projectId)
      return
    }
    def txt(node: com.fasterxml.jackson.databind.JsonNode, f: String): String = {
      val n = node.get(f); if (n != null) n.asText("") else ""
    }
    val agentIds = jobsNode.elements().asScala
      .map(j => txt(j, "agentId")).filter(_.nonEmpty).toSet

    if (agentIds.isEmpty) { System.out.println("Empty set\n"); return }

    // 2. Fetch all categories, scan each for the matching agents
    val catsNode = OpenApiHttp.getJson(apiClient(), "/webrobot/api/categories")
    val catIds = if (catsNode != null && catsNode.isArray)
      catsNode.elements().asScala.map(c => c.get("id").asText("")).filter(_.nonEmpty).toList
    else List.empty

    val dg = new DataGrid("id", "name", "description", "categoryId", "createdAt")
    catIds.foreach { catId =>
      val agentsNode = OpenApiHttp.getJson(apiClient(), "/webrobot/api/agents/" + catId)
      if (agentsNode != null && agentsNode.isArray)
        agentsNode.elements().asScala
          .filter(a => agentIds.contains(txt(a, "id")))
          .foreach(a => dg.add(txt(a,"id"), txt(a,"name"), txt(a,"description"), txt(a,"categoryId"), txt(a,"createdAt")))
    }

    if (dg.size > 0) { dg.render; System.out.println(dg.size + " rows in set\n") }
    else System.out.println("Empty set\n")
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
