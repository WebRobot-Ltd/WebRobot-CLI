package org.webrobot.cli.commands
import java.time.DateTimeException

import picocli.CommandLine.{Command, Option}
import WebRobot.Cli.Sdk.model.{Concept, ConceptAttribute, Create_conceptRequest, Create_projectRequest, Delete_conceptRequest, Delete_projectRequest, Get_all_conceptsRequest, Get_all_conceptsResult, Get_all_projectsRequest, Get_all_projectsResult, Get_concept_from_idRequest, Project, Update_conceptRequest, Update_projectRequest}
import com.amazonaws.opensdk.config.{ConnectionConfiguration, TimeoutConfiguration}
import org.webrobot.cli.RunWebRobotCli
import org.webrobot.cli.utils.DataGrid
import picocli.CommandLine.Parameters

import scala.collection.JavaConverters._
import scala.collection.mutable.ArrayBuffer

@Command(name = "delete", sortOptions = false,
  description = Array(
    "delete concept"),
  footer = Array()
)
class RunDeleteConceptCommand extends BaseSubCommand {

  @Option(names = Array("-p", "--projectId"), description = Array("set the project id"),required = true) private var projectId : String = ""
  @Option(names = Array("-b", "--botId"), description = Array("set the bot id"),required = true) private var botId : String = ""
  @Option(names = Array("-c", "--conceptId"), description = Array("set the concept id"),required = true) private var conceptId : String = ""
  override def startRun(): Unit =
  {
    this.init()
    var deleteConceptRequest : Delete_conceptRequest = new Delete_conceptRequest().sdkRequestConfig(this.getCustomSdkRequestConfig())

    deleteConceptRequest.setBotId(botId)
    deleteConceptRequest.setProjectId(projectId)
    deleteConceptRequest.setConceptId(conceptId)

    sdkClient.delete_concept(deleteConceptRequest)
  }
}

@Command(name = "list", sortOptions = false,
  description = Array(
    "list concepts"),
  footer = Array()
)
class RunListConceptCommand extends BaseSubCommand {
  @Option(names = Array("-p", "--projectId"), description = Array("set the project id"),required = true) private var projectId : String = ""
  @Option(names = Array("-b", "--botId"), description = Array("set the bot id"),required = true) private var botId : String = ""
  override def startRun(): Unit =
  {
    this.init()
    var conceptListRequest : Get_all_conceptsRequest = new Get_all_conceptsRequest().sdkRequestConfig(this.getCustomSdkRequestConfig())
    conceptListRequest.setProjectId(projectId)
    conceptListRequest.setBotId(botId)
    conceptListRequest.setTypeAttribute("AUTOMATIC")
    var conceptListResult : Get_all_conceptsResult = sdkClient.get_all_concepts(conceptListRequest)
    var concepts : Seq[Concept] = conceptListResult.getListConcepts().getConcepts().asScala
    this.dataGrid = new DataGrid("Id","Name","Created","AttributeName","AttributeType")
    concepts.foreach(item => {
      item.getAttributes().asScala.foreach(attr =>
        this.dataGrid.add(item.getId,item.getName,item.getCreatedTime, attr.getName,attr.getTypeAttribute())
      )
    }
    )
    if (this.dataGrid.size > 0) {
      this.dataGrid.render
      System.out.println(this.dataGrid.size + " rows in set\n")
    }
    else System.out.println("Empty set\n")
  }
}
@Command(name = "update", sortOptions = false,
  description = Array(
    "update project"),
  footer = Array()
)
class RunUpdateConceptCommand extends BaseSubCommand {

  @Option(names = Array("-p", "--projectId"), description = Array("set the project id"),required = true) private var projectId : String = ""
  @Option(names = Array("-b", "--botId"), description = Array("set the bot id"),required = true) private var botId : String = ""
  @Option(names = Array("-n", "--name"), description = Array("set the concept name"),required = true) private var name : String = ""
  @Option(names = Array("-i", "--id"), description = Array("set the concept id"),required = true) private var id : String = ""
  @Parameters(description = Array("one or more concept Attributes")) val conceptAttributes: Array[String] = null

  override def startRun(): Unit =
  {
    this.init()
    var conceptRequest : Update_conceptRequest = new Update_conceptRequest().sdkRequestConfig(this.getCustomSdkRequestConfig())
    conceptRequest.setProjectId(projectId)
    conceptRequest.setBotId(botId)
    conceptRequest.setConceptId(id)

    var getConceptRequest = new Get_concept_from_idRequest()
    getConceptRequest.setProjectId(projectId)
    getConceptRequest.setBotId(botId)
    getConceptRequest.setConceptId(id)
    getConceptRequest.setTypeAttribute("AUTOMATIC")
    var result = sdkClient.get_concept_from_id(getConceptRequest)

    var concept =  result.getConcept()
    concept.setApikey(RunWebRobotCli.config.getString("apiKey"))                                      //check if the config key is present
    concept.setBoiId(botId)
    concept.setName(name)
    concept.setProjectId(projectId)
    concept.setCreatedTime(java.time.LocalDateTime.now().toString)
    var attributes = new ArrayBuffer[ConceptAttribute]()
    if(conceptAttributes != null)
    {
      conceptAttributes.foreach(attribute => {
        var conceptAttribute = new ConceptAttribute()
        conceptAttribute.setIsRelaxedXpath(false)
        conceptAttribute.setMultipleValues(false)
        conceptAttribute.setTypeAttribute("AUTOMATIC")
        conceptAttribute.setProjectId(projectId)
        conceptAttribute.setApikey(RunWebRobotCli.config.getString("apiKey"))
        conceptAttribute.setBotId(botId)
        conceptAttribute.setCreatedTime(java.time.LocalDateTime.now().toString)
        attributes.append(conceptAttribute)
      })
    }
    concept.setAttributes(attributes.asJava)
    concept.setName(name)
    concept.setCreatedTime(java.time.LocalDateTime.now().toString)
    conceptRequest.setConcept(concept)
    sdkClient.update_concept(conceptRequest)
  }
}

@Command(name = "add", sortOptions = false,
  description = Array(
    "Add new concept"),
  footer = Array(),
  subcommands = Array()
)
class RunAddConceptCommand extends BaseSubCommand {

  @Option(names = Array("-p", "--projectId"), description = Array("set the project id"),required = true) private var projectId : String = ""
  @Option(names = Array("-b", "--botId"), description = Array("set the bot id"),required = true) private var botId : String = ""
  @Option(names = Array("-n", "--name"), description = Array("set the concept name"),required = true) private var name : String = ""
  @Parameters(description = Array("one or more concept Attributes")) val conceptAttributes: Array[String] = null

  override def startRun(): Unit =
  {
    this.init()
    var conceptRequest : Create_conceptRequest = new Create_conceptRequest().sdkRequestConfig(this.getCustomSdkRequestConfig())
    conceptRequest.setProjectId(projectId)
    conceptRequest.setBotId(botId)
    var concept = new Concept()
    concept.setApikey(RunWebRobotCli.config.getString("apiKey"))                                      //check if the config key is present
    concept.setBoiId(botId)
    concept.setName(name)
    concept.setProjectId(projectId)
    concept.setCreatedTime(java.time.LocalDateTime.now().toString)
    var attributes = new ArrayBuffer[ConceptAttribute]()
    if(conceptAttributes != null)
      {
        conceptAttributes.foreach(attribute => {
        var conceptAttribute = new ConceptAttribute()
          conceptAttribute.setIsRelaxedXpath(false)
          conceptAttribute.setMultipleValues(false)
          conceptAttribute.setTypeAttribute("AUTOMATIC")
          conceptAttribute.setProjectId(projectId)
          conceptAttribute.setApikey(RunWebRobotCli.config.getString("apiKey"))
          conceptAttribute.setBotId(botId)
          conceptAttribute.setCreatedTime(java.time.LocalDateTime.now().toString)
          attributes.append(conceptAttribute)
        })
      }
    concept.setAttributes(attributes.asJava)
    concept.setName(name)
    concept.setCreatedTime(java.time.LocalDateTime.now().toString)
    conceptRequest.setConcept(concept)
    sdkClient.create_concept(conceptRequest)
  }
}

@Command(name = "concept", sortOptions = false,
  description = Array(
    "Manage WebRobot Concepts (Insert,Update,Remove,List) for supervisioned wrapper induction"),
  footer = Array(),
  subcommands = Array(classOf[RunAddConceptCommand],classOf[RunUpdateConceptCommand],classOf[RunListConceptCommand],classOf[RunDeleteConceptCommand])
)
class RunConceptCommand   extends Runnable  {

  def run(): Unit = {

    println("Run Concept Command: add|update|list|delete")
  }
}