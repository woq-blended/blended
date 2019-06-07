package blended.mgmt.base.internal

import java.io.{File, FileInputStream, FileOutputStream}
import java.text.SimpleDateFormat
import java.util.Date
import java.util.concurrent.atomic.AtomicBoolean
import java.util.zip.{ZipEntry, ZipOutputStream}

import blended.container.context.api.ContainerContext
import blended.util.StreamCopySupport
import blended.util.logging.Logger
import org.osgi.framework.BundleContext

import scala.util.control.NonFatal

/**
 * Note: The fact that this class has the same name as it's trait is required by the MBean spec.
 */
class FrameworkService(bundleContext : BundleContext, ctContext : ContainerContext)
  extends FrameworkServiceMBean {

  private[this] val log = Logger[FrameworkService]
  private[this] val restarting : AtomicBoolean = new AtomicBoolean(false)

  override def restartContainer(reason : String, saveLogs : Boolean) : Unit = {

    val cfg = ctContext.getContainerConfig()
    val saveLogsPath = "blended.saveLogsOnRestart"

    try {
      val frameworkBundle = bundleContext.getBundle(0)

      val alreadyRestarting = restarting.getAndSet(true)

      if (!alreadyRestarting) {
        val now = new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date())

        val msg =
          s"""
             |---------------------------------------------------------------------------------------------------------
             | Container restart initiated : $now
             | Reason                      : $reason
             |---------------------------------------------------------------------------------------------------------
        """.stripMargin

        log.warn(msg)

        val saveLogsConfigured : Boolean = cfg.hasPath(saveLogsPath) && cfg.getBoolean(saveLogsPath)

        if (saveLogs & saveLogsConfigured) createLogArchive(now)

        frameworkBundle.update()
      } else {
        log.warn("Ignoring container restart command because framework is already restarting.")
      }
    } catch {
      case NonFatal(e) => log.error(e)("Could not restart container")
    }
  }

  private[this] def createLogArchive(timestamp : String) : Unit = {

    val archiveName = s"restart-$timestamp.zip"

    val logDir = new File(ctContext.getContainerLogDirectory())
    log.info(s"Creating log archive from directory [${logDir.getAbsolutePath()}]")

    val logFiles = logDir.list( (dir : File, name : String) => {
      val f = new File(dir, name)
      f.isFile() && !name.startsWith("restart")
    }).toList
    log.info(s"Files :[$logFiles]")

    try {
      val out = new ZipOutputStream(new FileOutputStream(new File(logDir, archiveName)))

      logFiles.foreach { logFile =>
        out.putNextEntry(new ZipEntry(logFile))
        val in = new FileInputStream(new File(logDir, logFile))
        StreamCopySupport.copyStream(in, out)
        in.close()
        out.closeEntry()
      }
      out.flush()
      out.close()
    } catch {
      case e : Exception => log.error(e)(s"Error creating log archive (${e.getMessage})")
    }
  }
}
