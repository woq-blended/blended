package blended.launcher.jvmrunner

import java.io.{File, FileInputStream}
import java.util.Properties

import blended.launcher.internal.{ARM, Logger}
import blended.updater.config.OverlayConfigCompanion

import scala.collection.JavaConverters._
import scala.util.Try
import scala.util.control.NonFatal

object JvmLauncher {

  private[this] lazy val log = Logger[JvmLauncher.type]

  private[this] lazy val launcher = new JvmLauncher()

  def main(args: Array[String]): Unit = {
    try {
      val exitVal = launcher.run(args)
      sys.exit(exitVal)
    } catch {
      case NonFatal(e) => sys.exit(1)
    }
  }
}

class JvmLauncher() {

  private[this] lazy val log = Logger[JvmLauncher]

  private[this] var runningProcess: Option[RunningProcess] = None

  val shutdownHook = new Thread("jvm-launcher-shutdown-hook") {
    override def run(): Unit = {
      log.info("Caught shutdown. Stopping process")
      runningProcess foreach { p =>
        p.stop()
      }
    }
  }
  Runtime.getRuntime.addShutdownHook(shutdownHook)

  def run(args: Array[String]): Int = {
    val config = checkConfig(parse(args)).get
    log.debug("JvmLauncherConfig = " + config)
    config.action match {
      case Some("start") =>
        log.debug("Request: start process")
        runningProcess match {
          case Some(_) =>
            log.debug("Process already running")
            sys.error("Already started")
          case None =>
            var retVal = 1
            do {
              if (retVal == 2) {
                log.debug("About to restart process")
                config.restartDelaySec match {
                  case Some(delay) =>
                    log.debug("Waiting " + delay + " seconds before start of process")
                    try {
                      Thread.sleep(delay * 1000)
                    } catch {
                      case e: InterruptedException =>
                        log.debug("Delay interrupted!")
                    }
                  case _ =>
                }
              } else {
                log.debug("About to start process")
              }

              val sysProps: Map[String, String] = {
                val sysPropsFile = File.createTempFile("jvmlauncher", ".properties")
                val p = startJava(
                  classpath = config.classpath,
                  jvmOpts = config.jvmOpts.toArray,
                  arguments = config.otherArgs.toArray ++ Array("--write-system-properties", sysPropsFile.getAbsolutePath()),
                  interactive = false,
                  errorsIntoOutput = false,
                  directory = new File(".").getAbsoluteFile()
                )
                val retVal = p.waitFor
                if (retVal != 0) {
                  log.error(s"The launcher-process to retrieve the JVM properties exited with an unexpected return code: ${retVal}. We try to read the properties file anyway!")
                }
                val props = new Properties()
                Try {
                  ARM.using(new FileInputStream(sysPropsFile)) { inStream =>
                    props.load(inStream)
                  }
                }.recover {
                  case e: Throwable => log.error("Could not read properties file", e)
                }
                sysPropsFile.delete()
                props.asScala.toList.toMap
              }

              val xmsOpt = sysProps.collect { case (OverlayConfigCompanion.Properties.JVM_USE_MEM, x) => s"-Xms${x}" }
              val xmxOpt = sysProps.collect { case (OverlayConfigCompanion.Properties.JVM_MAX_MEM, x) => s"-Xmx${x}" }
              val ssOpt = sysProps.collect { case (OverlayConfigCompanion.Properties.JVM_STACK_SIZE, x) => s"-Xss${x}" }

              val p = startJava(
                classpath = config.classpath,
                jvmOpts = (config.jvmOpts ++ xmsOpt ++ xmxOpt ++ ssOpt ++ sysProps.map { case (k, v) => s"-D${k}=${v}" }).toArray,
                arguments = config.otherArgs.toArray,
                interactive = true,
                errorsIntoOutput = false,
                directory = new File(".").getAbsoluteFile()
              )
              log.debug("Process started: " + p)
              runningProcess = Option(p)
              retVal = p.waitFor
              log.debug("Process finished with return code: " + retVal)
              runningProcess = None
            } while (retVal == 2)
            retVal
        }
      case Some("stop") =>
        log.debug("Request: stop process")
        runningProcess match {
          case None =>
            log.debug("No process running")
            sys.error("Not started")
          case Some(p) =>
            p.stop()
        }
      case a @ _ =>
        sys.error(s"Not a valid action : [$a]")
    }
  }

  case class Config(
      classpath: Seq[File] = Seq(),
      otherArgs: Seq[String] = Seq(),
      action: Option[String] = None,
      jvmOpts: Seq[String] = Seq(),
      restartDelaySec: Option[Int] = None) {

    private[this] lazy val prettyPrint : String =
      s"""${getClass().getSimpleName()}(
         |classpath=
         |${classpath.mkString("  ", "\n  ", "")},
         |action=${action},
         |otherArgs=
         |${otherArgs.mkString("  ", "\n  ", "")}
         |)""".stripMargin


    override def toString(): String = prettyPrint
  }

  def parse(args: Seq[String], initialConfig: Config = Config()): Config = {
    args match {
      case Seq() =>
        initialConfig
      case Seq("--", rest @ _*) =>
        initialConfig.copy(otherArgs = rest)
      case Seq("start", rest @ _*) if initialConfig.action.isEmpty =>
        parse(rest, initialConfig.copy(action = Option("start")))
      case Seq("stop", rest @ _*) if initialConfig.action.isEmpty =>
        parse(rest, initialConfig.copy(action = Option("stop")))
      case Seq(cp, rest @ _*) if initialConfig.classpath.isEmpty && cp.startsWith("-cp=") =>
        // Also support ":" on non-windows platform
        val cps = cp.substring("-cp=".length).split("[;]").toSeq.map(_.trim()).filter(!_.isEmpty).map(new File(_))
        parse(rest, initialConfig.copy(classpath = cps))
      case Seq(delay, rest @ _*) if initialConfig.restartDelaySec.isEmpty && delay.startsWith("-restartDelay=") =>
        val delaySec = delay.substring("-restartDelay=".length).toInt
        parse(rest, initialConfig.copy(restartDelaySec = Option(delaySec)))
      case Seq(jvmOpt, rest @ _*) if jvmOpt.startsWith("-jvmOpt=") =>
        val opt = jvmOpt.substring("-jvmOpt=".length).trim()
        parse(rest, initialConfig.copy(jvmOpts = initialConfig.jvmOpts ++ Seq(opt).filter(!_.isEmpty)))
      case _ =>
        sys.error("Cannot parse arguments: " + args)
    }
  }

  def checkConfig(config: Config): Try[Config] = Try {
    if (config.action.isEmpty) sys.error("Missing arguments for action: start|stop")
    if (config.classpath.isEmpty) Console.err.println("Waring: No classpath given")
    config
  }

  def startJava(classpath: Seq[File],
    jvmOpts: Array[String],
    arguments: Array[String],
    interactive: Boolean = false,
    errorsIntoOutput: Boolean = true,
    directory: File = new File(".")): RunningProcess = {

    log.debug("About to run Java process")

    // lookup java by JAVA_HOME env variable
    val javaHome = System.getenv("JAVA_HOME")
    val java =
      if (javaHome != null) s"${
        javaHome
      }/bin/java"
      else "java"
    log.debug("Using java executable: " + java)

    val cpArgs = classpath match {
      case null | Seq() => Array[String]()
      case cp => Array("-cp", pathAsArg(classpath))
    }
    log.debug("Using classpath args: " + cpArgs.mkString(" "))

    val propArgs = System.getProperties.asScala.map(p => s"-D${
      p._1
    }=${
      p._2
    }").toArray[String]
    log.debug("Using property args: " + propArgs.mkString(" "))

    val command = Array(java) ++ cpArgs ++ jvmOpts ++ propArgs ++ arguments

    val pb = new ProcessBuilder(command: _*)
    log.debug("Run command: " + command.mkString(" "))
    pb.environment().putAll(sys.env.asJava)
    pb.directory(directory)
    // if (!env.isEmpty) env.foreach { case (k, v) => pb.environment().put(k, v) }
    val p = pb.start

    new RunningProcess(p, errorsIntoOutput, interactive)
  }

  /**
   * Converts a Seq of files into a string containing the absolute file paths concatenated with the platform specific path separator (":" on Unix, ";" on Windows).
   */
  def pathAsArg(paths: Seq[File]): String = paths.map(p => p.getPath).mkString(File.pathSeparator)

}