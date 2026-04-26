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
      case _ => {
        ""
      }
    }
    message
  }
}
