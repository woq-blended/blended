package blended.launcher

import java.io.File

import de.tototec.cmdoption.CmdOption

class Cmdline {

  @CmdOption(names = Array("--config", "-c"), args = Array("FILE"),
    description = "Configuration file",
    conflictsWith = Array("--profile", "--profile-lookup"))
  def setPonfigFile(file : String) : Unit = configFile = Option(file)

  var configFile : Option[String] = None

  @CmdOption(names = Array("--help", "-h"), description = "Show this help", isHelp = true)
  var help : Boolean = false

  @CmdOption(names = Array("--profile", "-p"), args = Array("profile"),
    description = "Start the profile from file or directory {0}",
    conflictsWith = Array("--profile-lookup", "--config"))
  def setProfileDir(dir : String) : Unit = profileDir = Option(dir)

  var profileDir : Option[String] = None

  @CmdOption(names = Array("--framework-restart", "-r"), args = Array("BOOLEAN"),
    description = "Should the launcher restart the framework after updates." +
      " If disabled and the framework was updated, the exit code is 2.")
  var handleFrameworkRestart : Boolean = true

  @CmdOption(names = Array("--profile-lookup", "-P"), args = Array("config file"),
    description = "Lookup to profile file or directory from the config file {0}",
    conflictsWith = Array("--profile", "--config"))
  def setProfileLookup(file : String) : Unit = profileLookup = Option(file)

  var profileLookup : Option[String] = None

  @CmdOption(
    names = Array("--reset-container-id"),
    description = "This will generate a new UUID identifying the container regardless one whether it already exists",
    conflictsWith = Array("--config", "--init-container-id")
  )
  var resetContainerId : Boolean = false

  @CmdOption(
    names = Array("--init-container-id"),
    description = "This will generate a new UUID identifying the container in case it does not yet exist",
    conflictsWith = Array("--config", "--reset-container-id")
  )
  var initContainerId : Boolean = false

  @CmdOption(
    names = Array("--write-system-properties"),
    args = Array("FILE"),
    description = "Show the additional system properties this launch configuration wants to set and exit"
  )
  def setWriteSystemProperties(file : String) : Unit = writeSystemProperties = Option(new File(file).getAbsoluteFile())

  var writeSystemProperties : Option[File] = None

  @CmdOption(
    names = Array("--strict"),
    description = "Start the container in strict mode (unresolved bundles or bundles failing to start terminate the container)"
  )
  var strict : Boolean = false

  @CmdOption(
    names = Array("--test"),
    description = "Just test the framework start and then exit"
  )
  var test : Boolean = false

}
