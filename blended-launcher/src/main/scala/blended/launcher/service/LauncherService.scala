package blended.launcher.service

import java.io.File
import org.apache.commons.daemon.Daemon
import org.apache.commons.daemon.DaemonContext
import org.apache.commons.daemon.DaemonInitException
import blended.launcher.Launcher
import org.osgi.framework.launch.Framework

class LauncherService() extends Daemon {

  private[this] var configFile: File = _
  private[this] var framework: Option[Framework] = None

  def init(daemonContext: DaemonContext): Unit = {
    // potentially called with super-user privileges

    val args = daemonContext.getArguments()
    configFile = args match {
      case Array(configFile) => new File(configFile).getAbsoluteFile()
      case _ => throw new DaemonInitException("Expected one argument: <configfile>")
    }
  }

  def start(): Unit = {
    val launcher = Launcher(configFile)
    val errors = launcher.validate()
    if (!errors.isEmpty) {
      throw new RuntimeException("Could not start the OSGi Framework. Details:\n" + errors.mkString("\n"))
    }
    framework = Option(launcher.start())
  }

  def stop(): Unit = {
    framework.map { f =>
      f.stop()
      f.waitForStop(0)
      framework = None
    }
  }

  def destroy(): Unit = {
  }

}