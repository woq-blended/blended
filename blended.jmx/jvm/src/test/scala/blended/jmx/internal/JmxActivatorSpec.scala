package blended.jmx.internal

import java.io.File

import blended.jmx.{BlendedMBeanServerFacade, OpenMBeanExporter, OpenMBeanMapper}
import blended.testsupport.BlendedTestSupport
import blended.testsupport.pojosr.{PojoSrTestHelper, SimplePojoContainerSpec}
import blended.testsupport.scalatest.LoggingFreeSpecLike
import javax.management.MBeanServer
import org.osgi.framework.BundleActivator

class JmxActivatorSpec extends SimplePojoContainerSpec
  with LoggingFreeSpecLike
  with PojoSrTestHelper {

  override def bundles : Seq[(String, BundleActivator)] = Seq(
    "blended.jmx" -> new BlendedJmxActivator()
  )

  override def baseDir : String = new File(BlendedTestSupport.projectTestOutput, "container").getAbsolutePath()

  "The JMX Activator" - {

    "should expose the platform MBean Server and a BlendedMBeanServerFacade as a service" in {
      mandatoryService[MBeanServer](registry, None)
      mandatoryService[BlendedMBeanServerFacade](registry, None)
    }

    "should expose a OpenMBeanMapper and a OpenMBeanExporter as a service" in {
      mandatoryService[OpenMBeanMapper](registry, None)
      mandatoryService[OpenMBeanExporter](registry, None)
    }


  }
}
