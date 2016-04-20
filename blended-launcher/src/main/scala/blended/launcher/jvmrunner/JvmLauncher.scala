package blended.launcher.jvmrunner

import java.io.File
import java.io.FileInputStream
import java.io.OutputStream
import java.io.InputStream
import java.util.Properties
import blended.launcher.internal.ARM
import blended.updater.config.OverlayConfig

import scala.util.Try
import scala.util.control.NonFatal
import org.slf4j.LoggerFactory
import java.io.IOException
import scala.collection.JavaConverters._
import blended.launcher.internal.Logger

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
      log.info("Catched shutdown. Stopping process")
      runningProcess foreach { p =>
        p.stop()
      }
    }
  }
  Runtime.getRuntime.addShutdownHook(shutdownHook)

  def run(args: Array[String]): Int = {
    val config = checkConfig(parse(args)).get
    log.debug("config: " + config)
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

              val xmsOpt = sysProps.collect { case (OverlayConfig.Properties.JVM_USE_MEM, x) => s"-Xms${x}" }
              val xmxOpt = sysProps.collect { case (OverlayConfig.Properties.JVM_MAX_MEM, x) => s"-Xmx${x}" }

              val p = startJava(
                classpath = config.classpath,
                jvmOpts = (config.jvmOpts ++ xmsOpt ++ xmxOpt ++ sysProps.map { case (k, v) => s"-D${k}=${v}" }).toArray,
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
      case _ =>
        sys.error("No action defined")
    }
  }

  case class Config(
    classpath: Seq[File] = Seq(),
    otherArgs: Seq[String] = Seq(),
    action: Option[String] = None,
    jvmOpts: Seq[String] = Seq()) {

    override def toString(): String = s"${getClass().getSimpleName()}(classpath=${classpath},action=${action},otherArgs=${otherArgs})"
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

    // val env: Map[String, String] = Map()

    val pb = new ProcessBuilder(command: _*)
    log.debug("Run command: " + command.mkString(" "))
    pb.environment().putAll(sys.env.asJava)
    pb.directory(directory)
    // if (!env.isEmpty) env.foreach { case (k, v) => pb.environment().put(k, v) }
    val p = pb.start

    new RunningProcess(p, errorsIntoOutput, interactive)
  }

  class RunningProcess(process: Process, errorsIntoOutput: Boolean, interactive: Boolean) {

    val errThread = asyncCopy(process.getErrorStream, if (errorsIntoOutput) Console.out else Console.err)
    val inThread = asyncCopy(process.getInputStream, Console.out, interactive)

    val in = System.in
    val out = process.getOutputStream

    val outThread = new Thread("StreamCopyThread") {
      setDaemon(true)

      override def run {
        try {
          while (true) {
            if (in.available > 0) {
              in.read match {
                case -1 =>
                case read =>
                  out.write(read)
                  out.flush
              }
            } else {
              Thread.sleep(50)
            }
          }
        } catch {
          case e: IOException => // ignore
          case e: InterruptedException => // this is ok
        }
      }
    }
    outThread.start()

    def waitFor(): Int = {
      try {
        process.waitFor
      } finally {
        process.getOutputStream().close()
        outThread.interrupt()
        process.getErrorStream().close()
        //        try {
        //          errThread.join()
        //        } finally {
        //        }
        process.getInputStream().close()
        //        try {
        //          inThread.join()
        //        } finally {
        //        }
      }
    }

    def stop(): Int = {
      out.close()
      outThread.interrupt()
      waitFor()
    }
  }

  /**
   * Starts a new thread which copies an InputStream into an Output stream. Does not close the streams.
   */
  def copyInThread(in: InputStream, out: OutputStream): Thread = asyncCopy(in, out, false)

  /**
   * Starts a new thread which copies an InputStream into an Output stream. Does not close the streams.
   */

  def asyncCopy(in: InputStream, out: OutputStream, immediately: Boolean = false): Thread =
    new Thread("StreamCopyThread") {
      setDaemon(true)

      override def run {
        try {
          copy(in, out, immediately)
        } catch {
          case e: IOException => // ignore
          case e: InterruptedException => // ok
        }
        out.flush()
      }

      start
    }

  /**
   * Copies an InputStream into an OutputStream. Does not close the streams.
   */
  def copy(in: InputStream, out: OutputStream, immediately: Boolean = false): Unit = {
    if (immediately) {
      while (true) {
        if (in.available > 0) {
          in.read match {
            case -1 =>
            case read =>
              out.write(read)
              out.flush
          }
        } else {
          Thread.sleep(50)
        }
      }
    } else {
      val buf = new Array[Byte](1024)
      var len = 0
      while ( {
        len = in.read(buf)
        len > 0
      }) {
        out.write(buf, 0, len)
      }
    }
  }

  /**
   * Converts a Seq of files into a string containing the absolute file paths concatenated with the platform specific path separator (":" on Unix, ";" on Windows).
   */
  def pathAsArg(paths: Seq[File]): String = paths.map(p => p.getPath).mkString(File.pathSeparator)

}