package org.webrobot.cli
import picocli.CommandLine
import picocli.CommandLine.{Command, IVersionProvider, Option, Parameters}
import java.io.File

import com.typesafe.config.{Config, ConfigFactory}

import collection.JavaConverters._
import picocli.CommandLine

object RunWebRobotCli extends App {
  private var webrobotCliCommand : WebRobotCliCommand = new WebRobotCliCommand();
  val commandLine = new CommandLine(webrobotCliCommand)
  var str_args : Array[String] =  args.toArray[String]
  val myConfigFile = new File("config.cfg")
  var config : Config = null
  if(myConfigFile.exists()) {
    val fileConfig = ConfigFactory.parseFile(myConfigFile)
    config = ConfigFactory.load(fileConfig)
    config = config.getConfig("webrobot.api.gateway").getConfig("credentials")
    val parsed = commandLine.parseWithHandler(new CommandLine.RunAll, args)
  }
  else
    {
      print("The config file is not present in the application path")
    }
}