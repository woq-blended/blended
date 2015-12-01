package blended.launcher.service

import java.io.File
import org.apache.commons.daemon.Daemon
import org.apache.commons.daemon.DaemonContext
import org.apache.commons.daemon.DaemonInitException
import blended.launcher.Launcher
import org.osgi.framework.launch.Framework
import java.util.Date
import blended.launcher.Launcher.RunningFramework

/**
 * Caution: This object must not have a corresponding companion class,
 * to trigger the scala compieler's abilitiy to create a class with
 * static forwarder methods for each method defined in this object.
 */
object LauncherServiceWindows {

  private[this] var framework: Option[RunningFramework] = None

  def main(args: Array[String]): Unit = {
    println("" + System.identityHashCode(this) + ": Called main with args: " + args.mkString(","))
    args match {
      case Array("start", rest @ _*) => start(rest.toArray)
      case Array("stop", rest @ _*) => stop(rest.toArray)
      case args => sys.error("Unsupported args given. Usage: <main> start|stop args...")
    }
  }

  def start(args: Array[String]): Unit = {
    framework match {
      case Some(_) =>
        sys.error("Framework already started")
      case None =>
        println("Starting framework")
        val cmdline = Launcher.parseArgs(args).get
        val configs = Launcher.readConfigs(cmdline)
        val launcher = Launcher.createAndPrepareLaunch(configs, cmdline.resetProfileProps || cmdline.initProfileProps, cmdline.initProfileProps)
        val f = new RunningFramework(launcher.start())
        framework = Option(f)
        val retVal = f.waitForStop()
        // Force the exit
        sys.exit(retVal)
    }
    println("Finished call start()")
  }

  def stop(args: Array[String]): Unit = {
    framework match {
      case Some(f) =>
        println("Stopping framework: " + f)
        f.framework.stop()
        val retVal = f.waitForStop()
        // Force the exit
        //      sys.exit(retVal)
        framework = None
      case None =>
        sys.error("No service running")
    }
    println("Finishded calls stop()")
  }

}