import java.nio.file.Files

import sbt.Keys._
import sbt._

object TestLogConfig extends AutoPlugin {

  object autoImport {
    val testlogLogToFile = settingKey[Boolean]("Specify whether the test logs should go to a file.")
    val testlogLogToConsole = settingKey[Boolean]("Specify whether the test logs should go to the console.")
    val testlogDirectory = settingKey[File]("The directory for collecting the test logs.")
    val testlogFileName = settingKey[String]("The log file name for the test logs.")
    val testlogPattern = settingKey[String]("The pattern used for the test logs.")
    val testlogDefaultLevel = settingKey[String]("The log level for the test root logger.")
    val testlogLogPackages = settingKey[Map[String, String]]("Package log level overrides for individual packages.")

    val testlogCreateConfig = taskKey[Seq[File]]("Create the log config for the test logs.")
  }

  import autoImport._

  override def projectSettings: Seq[Def.Setting[_]] = inConfig(Test)(Seq(
    testlogLogToFile := false,
    testlogLogToConsole := true,
    testlogFileName := name.value + "-test.log",
    testlogPattern := "%d{yyyy-MM-dd-HH:mm.ss.SSS} | %8.8r | %-5level [%t] %logger{36} : %msg%n",
    testlogDefaultLevel := "INFO",
    testlogLogPackages := Map.empty,

    testlogCreateConfig := {
      ConfigGenerator(
        configFile = target.value / "logback-test.xml",
        logFileName = (testlogDirectory.value / testlogFileName.value).getAbsolutePath(),
        pattern = testlogPattern.value,
        defaultLogLevel = testlogDefaultLevel.value,
        logPackages = testlogLogPackages.value,
        logToFile = testlogLogToFile.value,
        logToConsole = testlogLogToConsole.value
      )
    }
  ))
}

object ConfigGenerator {

  private[this] def logbackTpl(
    logFileName : String,
    pattern : String,
    defaultLogLevel : String,
    logPackages : Map[String, String],
    logToFile : Boolean,
    logToConsole : Boolean
  ) : Seq[String] = {

    val fileConfig : Seq[String] = if (logToFile) {
      Seq(
        "",
        "  <appender name=\"FILE\" class=\"ch.qos.logback.core.FileAppender\">",
        s"    <file>$logFileName</file>",
        "    <encoder>",
        s"      <pattern>$pattern</pattern>",
        "    </encoder>",
        "  </appender>",
        "",
        "  <appender name=\"ASYNC_FILE\" class=\"ch.qos.logback.classic.AsyncAppender\">",
        "    <appender-ref ref=\"FILE\" />",
        "  </appender>",
        ""
      )
    } else {
      Seq.empty
    }

    val outConfig : Seq[String] = if (logToConsole) {
      Seq(
        "",
        "<appender name=\"STDOUT\" class=\"ch.qos.logback.core.ConsoleAppender\">",
        "  <encoder>",
        s"    <pattern>$pattern</pattern>",
        "  </encoder>",
        "</appender>",
        ""
      )
    } else {
      Seq.empty
    }

    Seq(
      "<?xml version=\"1.0\" ?>",
      "",
      "<configuration>"
    ) ++
      fileConfig ++
      outConfig ++
      logPackages.map{ case (k,v) => s"""<logger name="$k" level="$v" />"""} ++
      Seq(
        "",
        s"""  <root level="$defaultLogLevel">""",
      ) ++
        (if (logToFile)    Seq("""    <appender-ref ref="FILE" />""") else Seq.empty) ++
        (if (logToConsole) Seq("""    <appender-ref ref="STDOUT" />""") else Seq.empty) ++
      Seq(
        "  </root>",
        "</configuration>"
      )
  }

  def apply(
    configFile : File,
    logFileName : String,
    pattern : String,
    defaultLogLevel : String,
    logPackages : Map[String, String],
    logToFile : Boolean,
    logToConsole : Boolean
  ) : Seq[java.io.File] = {

    Files.createDirectories(configFile.getParentFile().toPath())
    IO.write(configFile, logbackTpl(
      logFileName = logFileName,
      pattern = pattern,
      defaultLogLevel = defaultLogLevel,
      logPackages = logPackages,
      logToFile = logToFile,
      logToConsole = logToConsole
    ).mkString("\n"))

    Seq(configFile)
  }
}
