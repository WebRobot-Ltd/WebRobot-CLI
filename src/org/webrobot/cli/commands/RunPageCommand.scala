package org.webrobot.cli.commands

import java.awt.PageAttributes
import java.time.DateTimeException

import picocli.CommandLine.{Command, Option}
import WebRobot.Cli.Sdk.model.{Concept, ConceptAttribute, Create_conceptRequest, Create_pageRequest, Create_projectRequest, Delete_conceptRequest, Delete_page_from_idRequest, Delete_projectRequest, Get_all_conceptsRequest, Get_all_conceptsResult, Get_all_pagesRequest, Get_all_pagesResult, Get_all_projectsRequest, Get_all_projectsResult, Get_concept_from_idRequest, Get_page_from_idRequest, Page, PageAttribute, Project, Update_conceptRequest, Update_pageRequest, Update_projectRequest}
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
class RunDeletePageCommand extends BaseSubCommand {

  @Option(names = Array("-p", "--projectId"), description = Array("set the project id"),required = true) private var projectId : String = ""
  @Option(names = Array("-b", "--botId"), description = Array("set the bot id"),required = true) private var botId : String = ""
  @Option(names = Array("-c", "--conceptId"), description = Array("set the concept id"),required = true) private var conceptId : String = ""
  override def startRun(): Unit =
  {
    this.init()
    var delete_page_idRequest : Delete_page_from_idRequest = new Delete_page_from_idRequest().sdkRequestConfig(this.getCustomSdkRequestConfig())

    delete_page_idRequest.setBotId(botId)
    delete_page_idRequest.setProjectId(projectId)
    delete_page_idRequest.setConceptId(conceptId)
    sdkClient.delete_page_from_id(delete_page_idRequest)
  }
}

@Command(name = "list", sortOptions = false,
  description = Array(
    "list concepts"),
  footer = Array()
)
class RunListPageCommand extends BaseSubCommand {
  @Option(names = Array("-p", "--projectId"), description = Array("set the project id"),required = true) private var projectId : String = ""
  @Option(names = Array("-b", "--botId"), description = Array("set the bot id"),required = true) private var botId : String = ""
  @Option(names = Array("-c", "--conceptId"), description = Array("set the conceptId"),required = true) private var conceptId : String = ""
  override def startRun(): Unit =
  {
    this.init()
    var pageListRequest : Get_all_pagesRequest = new Get_all_pagesRequest().sdkRequestConfig(this.getCustomSdkRequestConfig())
    pageListRequest.setProjectId(projectId)
    pageListRequest.setBotId(botId)
    pageListRequest.setConceptId("AUTOMATIC")
    var pageListResult : Get_all_pagesResult = sdkClient.get_all_pages(pageListRequest)
    var pages : Seq[Page] = pageListResult.getListPages().getPages().asScala
    this.dataGrid = new DataGrid("Id","Html","Url","Created","Name","Value")
    pages.foreach(item => {
      item.getAttributes().asScala.foreach(attr =>
        this.dataGrid.add(item.getId,item.getHtml,item.getUrl, item.getCreatedTime,attr.getName,attr.getValue)
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
    "update page"),
  footer = Array()
)
class RunUpdatePageCommand extends BaseSubCommand {

  @Option(names = Array("-i", "--id"), description = Array("set the project id"),required = true) private var id : String = ""
  @Option(names = Array("-p", "--projectId"), description = Array("set the project id"),required = true) private var projectId : String = ""
  @Option(names = Array("-b", "--botId"), description = Array("set the bot id"),required = true) private var botId : String = ""
  @Option(names = Array("-c", "--conceptId"), description = Array("set the conceptId"),required = true) private var conceptId : String = ""
  @Option(names = Array("-h", "--html"), description = Array("set the html page of sample"),required = true) private var html : String = null
  @Option(names = Array("-u", "--url"), description = Array("set url page of sample"),required = true) private var url : String = null
  @Parameters(description = Array("one or more concept Attributes")) val pageAttributes: Array[String] = null

  override def startRun(): Unit =
  {
    this.init()

    var getPageRequest = new Get_page_from_idRequest()
    getPageRequest.setProjectId(projectId)
    getPageRequest.setBotId(botId)
    getPageRequest.setConceptId(id)
    getPageRequest.setPageId(id)
    var pageResult = sdkClient.get_page_from_id(getPageRequest)
    var mypage =  pageResult.getPage()
    var pageRequest : Update_pageRequest = new Update_pageRequest().sdkRequestConfig(this.getCustomSdkRequestConfig())
    pageRequest.setProjectId(projectId)
    pageRequest.setBotId(botId)
    pageRequest.setConceptId(conceptId)
    mypage.setApikey(RunWebRobotCli.config.getString("apiKey"))
    mypage.setBotId(botId)
    mypage.setProjectId(projectId)
    mypage.setConceptId(conceptId)
    mypage.setHtml(html)
    mypage.setUrl(url)
    mypage.setCreatedTime(java.time.LocalDateTime.now().toString)
    var attributes = new ArrayBuffer[PageAttribute]()
    pageAttributes.foreach(item =>{
      var pageAttribute = new PageAttribute()
      pageAttribute.setName(item.split(':')(0))
      pageAttribute.setValue(item.split(':')(1))
      attributes.append(pageAttribute)
    })
    mypage.setAttributes(attributes.asJava)
    var page = sdkClient.update_page(pageRequest)

  }
}

@Command(name = "add", sortOptions = false,
  description = Array(
    "Add new page"),
  footer = Array(),
  subcommands = Array()
)
class RunAddPageCommand extends BaseSubCommand {

  @Option(names = Array("-p", "--projectId"), description = Array("set the project id"),required = true) private var projectId : String = ""
  @Option(names = Array("-b", "--botId"), description = Array("set the bot id"),required = true) private var botId : String = ""
  @Option(names = Array("-c", "--conceptId"), description = Array("set the conceptId"),required = true) private var conceptId : String = ""
  @Option(names = Array("-h", "--html"), description = Array("set the html page of sample"),required = true) private var html : String = null
  @Option(names = Array("-u", "--url"), description = Array("set url page of sample"),required = true) private var url : String = null
  @Parameters(description = Array("one or more concept Attributes")) val pageAttributes: Array[String] = null

  override def startRun(): Unit =
  {
    this.init()
    var pageRequest : Create_pageRequest = new Create_pageRequest().sdkRequestConfig(this.getCustomSdkRequestConfig())
    pageRequest.setProjectId(projectId)
    pageRequest.setBotId(botId)
    pageRequest.setConceptId(conceptId)
    var mypage : Page = new Page()
    mypage.setApikey(RunWebRobotCli.config.getString("apiKey"))
    mypage.setBotId(botId)
    mypage.setProjectId(projectId)
    mypage.setConceptId(conceptId)
    mypage.setHtml(html)
    mypage.setUrl(url)
    mypage.setCreatedTime(java.time.LocalDateTime.now().toString)
    var attributes = new ArrayBuffer[PageAttribute]()
    pageAttributes.foreach(item =>{
        var pageAttribute = new PageAttribute()
        pageAttribute.setName(item.split(':')(0))
        pageAttribute.setValue(item.split(':')(1))
        attributes.append(pageAttribute)
    })
    mypage.setAttributes(attributes.asJava)
    var page = sdkClient.create_page(pageRequest)

  }
}

@Command(name = "concept", sortOptions = false,
  description = Array(
    "Manage WebRobot Concepts (Insert,Update,Remove,List) for supervisioned wrapper induction"),
  footer = Array(),
  subcommands = Array(classOf[RunAddPageCommand],classOf[RunUpdatePageCommand],classOf[RunListPageCommand],classOf[RunDeletePageCommand])
)
class RunPageCommand   extends Runnable  {

  def run(): Unit = {

    println("Run Page Command: add|update|list|delete")
  }
}