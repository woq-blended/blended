package blended.launcher.jvmrunner

import java.io.File

import scala.annotation.tailrec
import scala.concurrent.duration._
import scala.util.Try

case class JvmLauncherConfig(
    classpath: Seq[File] = Seq(),
    otherArgs: Seq[String] = Seq(),
    action: Option[String] = None,
    jvmOpts: Seq[String] = Seq(),
    interactive: Boolean = true,
    shutdownTimeout: FiniteDuration = 5.seconds,
    restartDelaySec: Option[Int] = None
) {

  private[this] lazy val prettyPrint: String =
    getClass().getSimpleName() +
      "(classpath=" + classpath +
      ",otherArgs=" + otherArgs +
      ",action=" + action +
      ",jvmOption=" + jvmOpts +
      ",interactive=" + interactive +
      ",shutdownTimeout=" + shutdownTimeout +
      ",restartDelaySec=" + restartDelaySec
  ")"

  override def toString(): String = prettyPrint
}

object JvmLauncherConfig {
  @tailrec
  def parse(args: Seq[String], initialConfig: JvmLauncherConfig = JvmLauncherConfig()): JvmLauncherConfig = {

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
        val seconds: Int = Integer.parseInt(maxShutdown.substring("-maxShutdown=".length()))
        parse(rest, initialConfig.copy(shutdownTimeout = seconds.seconds))
      case _ =>
        sys.error("Cannot parse arguments: " + args)
    }
  }

  def checkConfig(config: JvmLauncherConfig): Try[JvmLauncherConfig] = Try {
    if (config.action.isEmpty) {
      sys.error("Missing arguments for action: start|stop")
    }

    if (config.classpath.isEmpty) {
      Console.err.println("Warning: No classpath given")
    }
    config
  }

}
