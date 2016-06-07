package blended.mgmt.base.internal

import java.util.Date

import blended.mgmt.base.FrameworkService
import org.osgi.framework.BundleContext
import org.slf4j.LoggerFactory

class FrameworkServiceImpl(bundleContext: BundleContext) extends FrameworkService {

  private[this] val log = LoggerFactory.getLogger(classOf[FrameworkServiceImpl])

  override def restartContainer(reason: String): Unit = {

    val msg =
      s"""
        |---------------------------------------------------------------------------------------------------------
        | Container restart initiated : ${new Date()}
        | Reason                      : ${reason}
        |---------------------------------------------------------------------------------------------------------
      """.stripMargin

    log.warn(msg)

    val frameworkBundle = bundleContext.getBundle(0)
    frameworkBundle.update()
  }
}
