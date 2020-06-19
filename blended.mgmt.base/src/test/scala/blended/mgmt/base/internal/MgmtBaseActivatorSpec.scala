package blended.mgmt.base.internal

import java.io.File
import java.lang.management.ManagementFactory

import blended.testsupport.BlendedTestSupport
import blended.testsupport.pojosr.{PojoSrTestHelper, SimplePojoContainerSpec}
import blended.testsupport.scalatest.LoggingFreeSpecLike
import javax.management.MBeanServer
import org.osgi.framework.BundleActivator
import org.scalatest.matchers.should.Matchers
import scala.concurrent.duration._

class MgmtBaseActivatorSpec extends SimplePojoContainerSpec
  with LoggingFreeSpecLike
  with PojoSrTestHelper
  with Matchers {

  override def bundles : Seq[(String, BundleActivator)] = Seq(
    "blended.mgmt.base" -> new MgmtBaseActivator()
  )

  override def baseDir : String = new File(BlendedTestSupport.projectTestOutput, "container").getAbsolutePath()

  "The Mgmt base activator" - {

    "should register a FrameworkService as OSGi service" in {
      mandatoryService[blended.mgmt.base.FrameworkService](registry, None)
    }

    "should register a FrameworkService as MBean" in {
      mandatoryService[blended.mgmt.base.FrameworkService](registry, None)
      ensureServiceMissing[MBeanServer](registry)()(implicitly, 1.second)
      val reg = registry.registerService(classOf[MBeanServer].getName(), ManagementFactory.getPlatformMBeanServer(), new java.util.Hashtable[String, Any]())
      try {
        mandatoryService[MBeanServer](registry, None)
      } finally {
        reg.unregister()
      }
      ensureServiceMissing[MBeanServer](registry)()(implicitly, 1.second)
    }
  }
}
