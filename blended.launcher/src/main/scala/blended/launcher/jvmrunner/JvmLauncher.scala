package blended.launcher.jvmrunner

import java.io.{File, FileInputStream}
import java.util.Properties

import scala.collection.JavaConverters._
import scala.util.Try
import scala.util.control.NonFatal
import blended.launcher.internal.ARM
import blended.updater.config.OverlayConfigCompanion
import blended.util.logging.Logger

import scala.annotation.tailrec
import scala.concurrent.duration._

object JvmLauncher {

  private[this] lazy val log = Logger[JvmLauncher.type]

  private[this] lazy val launcher = new JvmLauncher()

  def main(args : Array[String]) : Unit = {
    try {
      val exitVal = launcher.run(args)
      sys.exit(exitVal)
    } catch {
      case NonFatal(e) => sys.exit(1)
    }
  }
}

/**
 * A small Java wrapper responsiblefor controlling the actual Container JVM.
 */
class JvmLauncher() {

  private[this] lazy val log = Logger[JvmLauncher]

  private[this] var runningProcess : Option[RunningProcess] = None

  private[this] val shutdownHook = new Thread("jvm-launcher-shutdown-hook") {
    override def run() : Unit = {
      log.info("Caught shutdown. Stopping process")
      runningProcess foreach { p =>
        p.stop()
      }
    }
  }
  Runtime.getRuntime.addShutdownHook(shutdownHook)

  def run(args : Array[String]) : Int = {
    val config = checkConfig(parse(args)).get
    log.debug("JvmLauncherConfig = " + config)
    config.action match {

      // Try to start the inner JVM
      case Some("start") =>
        log.debug("Request: start process")
        runningProcess match {
          case Some(_) =>
            log.debug("Process already running")
            sys.error("Already started")
          case None =>
            var retVal = 1
            do {
              // If the container JVM terminated with exit code 2, we will restart
              // the container JVM.
              if (retVal == 2) {
                config.restartDelaySec match {
                  // In some cases we need a cool down period before we restart the container
                  // JVM. In that case we will wait for the specified number of seconds.
                  case Some(delay) =>
                    log.debug("Waiting " + delay + " seconds before restarting the container.")
                    try {
                      Thread.sleep(delay * 1000)
                    } catch {
                      case e : InterruptedException =>
                        log.debug("Delay interrupted!")
                    }
                  case _ =>
                }
                log.debug("About to restart the container process.")
              } else {
                log.debug("About to start process")
              }

              // Starting the container requires 2 steps:
              // First, we will start the container with the parameter --write-system-properties.
              // This will cause that the container runtime evaluates the current profile and it's
              // overlays. In this mode the container will dump the determined System Properties
              // into a file and terminate.
              //
              // We will then start the container with the calculated system properties.
              //
              // This gives us the opportunity to handle properties configuring the JVM itself with
              // the blended overlay mechanism.

              log.info("-" * 80)
              log.info("Starting container in write properties mode")
              log.info("-" * 80)
              val sysProps : Map[String, String] = {
                val sysPropsFile = File.createTempFile("jvmlauncher", ".properties")
                val p = startJava(
                  classpath = config.classpath,
                  jvmOpts = config.jvmOpts.toArray,
                  arguments = config.otherArgs.toArray ++ Array("--write-system-properties", sysPropsFile.getAbsolutePath()),
                  interactive = false,
                  errorsIntoOutput = false,
                  shutdownTimeout = config.shutdownTimeout,
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
                  case e : Throwable => log.error(e)("Could not read properties file")
                }
                sysPropsFile.delete()
                props.asScala.toList.toMap
              }

              // Now we can extract the JVM memory settings if given
              val xmsOpt = sysProps.collect { case (OverlayConfigCompanion.Properties.JVM_USE_MEM, x) => s"-Xms${x}" }
              val xmxOpt = sysProps.collect { case (OverlayConfigCompanion.Properties.JVM_MAX_MEM, x) => s"-Xmx${x}" }
              val ssOpt = sysProps.collect { case (OverlayConfigCompanion.Properties.JVM_STACK_SIZE, x) => s"-Xss${x}" }

              log.info("-" * 80)
              log.info("Starting blended container instance")
              log.info("-" * 80)
              val p = startJava(
                classpath = config.classpath,
                jvmOpts = (config.jvmOpts ++ xmsOpt ++ xmxOpt ++ ssOpt ++ sysProps.map { case (k, v) => s"-D${k}=${v}" }).toArray,
                arguments = config.otherArgs.toArray,
                interactive = config.interactive,
                errorsIntoOutput = false,
                shutdownTimeout = config.shutdownTimeout,
                directory = new File(".").getAbsoluteFile()
              )
              log.debug("Process started: " + p)
              runningProcess = Option(p)
              retVal = p.waitFor
              log.info("-" * 80)
              log.info(s"Blended container instance terminated with exit code [$retVal]")
              log.info("-" * 80)
              runningProcess = None
            } while (retVal == 2)
            retVal
        }

      // try to stop the inner JVM
      case Some("stop") =>
        log.debug("Request: stop process")
        runningProcess match {
          case None =>
            log.debug("No process running")
            sys.error("Not started")
          case Some(p) =>
            p.stop()
        }

      // All other commands are considered to be errors
      case a @ _ =>
        sys.error(s"Not a valid action : [$a]")
    }
  }

  case class JvmLauncherConfig(
    classpath : Seq[File] = Seq(),
    otherArgs : Seq[String] = Seq(),
    action : Option[String] = None,
    jvmOpts : Seq[String] = Seq(),
    interactive : Boolean = true,
    shutdownTimeout : FiniteDuration = 5.seconds,
    restartDelaySec : Option[Int] = None
  ) {

    private[this] lazy val prettyPrint : String =
      s"""${getClass().getSimpleName()}(
         |classpath=
         |${classpath.mkString("  ", "\n  ", "")},
         |action=${action},
         |otherArgs=
         |${otherArgs.mkString("  ", "\n  ", "")}
         |)""".stripMargin

    override def toString() : String = prettyPrint
  }

  @tailrec
  final def parse(args : Seq[String], initialConfig : JvmLauncherConfig = JvmLauncherConfig()) : JvmLauncherConfig = {

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
      case Seq(interactive, rest @ _*) if interactive.startsWith("-interactive") =>
        val iAct = interactive.substring("-interactive=".length).toBoolean
        parse(rest, initialConfig.copy(interactive = iAct))
      case Seq(maxShutdown, rest @ _*) if maxShutdown.startsWith("-maxShutdown") =>
        val seconds : Int = Integer.parseInt(maxShutdown.substring("-maxShutdown=".length()))
        parse(rest, initialConfig.copy(shutdownTimeout = seconds.seconds))
      case _ =>
        sys.error("Cannot parse arguments: " + args)
    }
  }

  private def checkConfig(config : JvmLauncherConfig) : Try[JvmLauncherConfig] = Try {
    if (config.action.isEmpty) {
      sys.error("Missing arguments for action: start|stop")
    }

    if (config.classpath.isEmpty) {
      Console.err.println("Warning: No classpath given")
    }
    config
  }

  private def startJava(
    classpath : Seq[File],
    jvmOpts : Array[String],
    arguments : Array[String],
    interactive : Boolean = false,
    errorsIntoOutput : Boolean = true,
    directory : File = new File("."),
    shutdownTimeout : FiniteDuration
  ) : RunningProcess = {

    log.debug("About to run Java process")

    // lookup java by JAVA_HOME env variable
    val java = Option(System.getenv("JAVA_HOME")) match {
      case Some(javaHome) => s"$javaHome/bin/java"
      case None           => "java"
    }

    log.debug("Using java executable: " + java)

    val cpArgs = Option(classpath) match {
      case None | Some(Seq()) => Array[String]()
      case Some(cp)           => Array("-cp", pathAsArg(classpath))
    }
    log.debug("Using classpath args: " + cpArgs.mkString(" "))

    log.debug(s"Using JVM options ${jvmOpts.mkString("[\n", "\n", "\n]")}")
    val command = Array(java) ++ cpArgs ++ jvmOpts ++ arguments

    log.debug(s"Using JVM arguments [${arguments.mkString("\n")}]")

    val pb = new ProcessBuilder(command : _*)
    log.debug("Run command: " + command.mkString(" "))
    pb.environment().putAll(sys.env.asJava)
    pb.directory(directory)
    // if (!env.isEmpty) env.foreach { case (k, v) => pb.environment().put(k, v) }
    val p = pb.start

    new RunningProcess(p, errorsIntoOutput, interactive, shutdownTimeout)
  }

  /**
   * Converts a Seq of files into a string containing the absolute file paths concatenated with the platform specific path separator (":" on Unix, ";" on Windows).
   */
  def pathAsArg(paths : Seq[File]) : String = paths.map(p => p.getPath).mkString(File.pathSeparator)

}
