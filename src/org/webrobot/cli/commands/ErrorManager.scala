package org.webrobot.cli.commands
import org.webrobot.cli.commands.models.ErrorException
import scala.collection.mutable

class ErrorManager {
  def run(ex: ErrorException): String =
  {
    var message = ex.getErrorType match {
      case "BotNotExist" => {
        if(!ex.getException.equals(""))
          "Error: bot is not present with this exception message:" + ex.getException
        else
          "Error: bot is not present"
      }
      case "ProjectNotExist" => {
        if(!ex.getException.equals(""))
         "Error: project is not present with this exception message:" + ex.getException
        else
          "Error: project is not present"
      }
      case "UpdateBotError" => {
        if(!ex.getException.equals(""))
         "Error: we can't update the bot instance with this exception message:" + ex.getException
        else
          "Error: we can't update the bot instance"
      }
      case "CreateBotError" => {
        if(!ex.getException.equals(""))
         "Error: we can't create the bot instance with this exception message:" + ex.getException
        else
          "Error: we can't create the bot instance"
      }
      case "GetBotError" => {
        if(!ex.getException.equals(""))
         "Error: we can't get the bot instance with this exception message:" + ex.getException
        else
          "Error: we can't get the bot instance"
      }
      case "GetAllBotsError" => {
        if(!ex.getException.equals(""))
         "Error: we can't get all bots instance with this exception message:" + ex.getException
        else
          "Error: we can't get all bots instance"
      }
      case "DeleteBotError" => {
        if(!ex.getException.equals(""))
         "Error: we can't delete bot instance with this exception message:" + ex.getException
        else
          "Error: we can't delete bot instance"
      }
      case "JobStopError" => {
        if(!ex.getException.equals(""))
         "Error: we can't stop the job instance with this exception message:" + ex.getException
        else
          "Error: we can't stop the job instance"
      }
      case "UpdateConceptAttributeError" => {
        if(!ex.getException.equals(""))
         "Error: we can't update the concept attribute with this exception message:" + ex.getException
        else
          "Error: we can't update the concept attribute"
      }
      case "CreateConceptAttributeError" => {
        if(!ex.getException.equals(""))
         "Error: we can't create the concept attribute with this exception message:" + ex.getException
        else
          "Error: we can't create the concept attribute"
      }
      case "ConceptNotExist" => {
        if(!ex.getException.equals(""))
         "Error: concept is not present with this exception message:" + ex.getException
        else
          "Error: concept is not present"
      }
      case "UpdateConceptPageError" => {
        if(!ex.getException.equals(""))
         "Error: we can't update the concept page with this exception message:" + ex.getException
        else
          "Error: we can't update the concept page"
      }
      case "CreatePageError" => {
        if(!ex.getException.equals(""))
         "Error: we can't create the page with this exception message:" + ex.getException
        else
          "Error: we can't create the page"
      }
      case "UpdateConceptError" => {
        if(!ex.getException.equals(""))
         "Error: we can't update the concept with this exception message:" + ex.getException
        else
          "Error: we can't update the concept"
      }
      case "GetConceptAttributeError" => {
        if(!ex.getException.equals(""))
         "Error: we can't get the concept attribute with this exception message:" + ex.getException
        else
          "Error: we can't get the concept attribute"
      }
      case "PageAttributeNotExist" => {
        if(!ex.getException.equals(""))
         "Error: Page attribute is not present with this exception message:" + ex.getException
        else
          "Error: Page attribute is not present"
      }
      case "GetConceptPageError" => {
        if(!ex.getException.equals(""))
         "Error: Concept page is not present with this exception message:" + ex.getException
        else
          "Error: Concept page is not present"
      }
      case "GetAllConceptAttributesError" => {
        if(!ex.getException.equals(""))
         "Error: to list all concept attributes with this exception message:" + ex.getException
        else
          "Error: to list all concept attributes"
      }
      case "AllPagesError" => {
        if(!ex.getException.equals(""))
         "Error: to list all pages with this exception message:" + ex.getException
        else
          "Error: to list all pages"
      }
      case "DeleteAttributeError" => {
        if(!ex.getException.equals(""))
         "Error: error to delete attribute with exception message:" + ex.getException
        else
          "Error: error to delete attribute"
      }
      case "DeleteConceptError" => {
        if(!ex.getException.equals(""))
         "Error: error to delete concept with exception message:" + ex.getException
        else
          "Error: error to delete concept"
      }
      case "ParserError" =>
      {
        if(!ex.getException.equals(""))
          "Error: error to parse the code with exception message:" + ex.getException
        else
          "Error: error to parse the code"
      }
      case _ => {
        ""
      }
    }
    message
  }
}
