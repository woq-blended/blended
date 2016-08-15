package blended.mgmt.base.internal

import java.io.{FileInputStream, FileOutputStream, File, FilenameFilter}
import java.text.SimpleDateFormat
import java.util.Date
import java.util.zip.{ZipEntry, ZipOutputStream}

import blended.container.context.ContainerContext
import blended.util.StreamCopySupport
import org.osgi.framework.BundleContext
import org.slf4j.LoggerFactory

class FrameworkService(bundleContext: BundleContext, ctContext: ContainerContext) extends FrameworkServiceMBean {

  private[this] val log = LoggerFactory.getLogger(classOf[FrameworkService])

  override def restartContainer(reason: String, saveLogs: Boolean): Unit = {

    val now = new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date())

    val msg =
      s"""
        |---------------------------------------------------------------------------------------------------------
        | Container restart initiated : $now
        | Reason                      : $reason
        |---------------------------------------------------------------------------------------------------------
      """.stripMargin

    log.warn(msg)

    if (saveLogs) createLogArchive(now)

    val frameworkBundle = bundleContext.getBundle(0)
    frameworkBundle.update()
  }

  private[this] def createLogArchive(timestamp: String) : Unit = {

    val archiveName = s"restart-${timestamp}.zip"

    val logDir = new File(ctContext.getContainerLogDirectory())
    log.info(s"Creating log archive from directory [${logDir.getAbsolutePath()}]")

    val logFiles = logDir.list(new FilenameFilter {
      override def accept(dir: File, name: String): Boolean = {
        val f = new File(dir, name)
        f.isFile() && !name.startsWith("restart")
      }
    }).toList
    log.info(s"Files :[$logFiles]")

    try {
      val out = new ZipOutputStream(new FileOutputStream(new File(logDir, archiveName)))

      logFiles.foreach { logFile =>
        out.putNextEntry(new ZipEntry(logFile))
        val in = new FileInputStream(new File(logDir,logFile))
        StreamCopySupport.copyStream(in, out)
        in.close()
        out.closeEntry()
      }
      out.flush()
      out.close()
    } catch {
      case e : Exception => log.error(s"Error creating log archive (${e.getMessage})", e)
    }
  }
}
