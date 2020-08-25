package blended.jmx.internal

import java.io.File

import blended.jmx.{BlendedMBeanServerFacade, ProductMBeanManager}
import blended.testsupport.BlendedTestSupport
import blended.testsupport.pojosr.{PojoSrTestHelper, SimplePojoContainerSpec}
import blended.testsupport.scalatest.LoggingFreeSpecLike
import javax.management.MBeanServer
import org.osgi.framework.BundleActivator
import blended.akka.internal.BlendedAkkaActivator

class JmxActivatorSpec extends SimplePojoContainerSpec
  with LoggingFreeSpecLike
  with PojoSrTestHelper {

  override def bundles : Seq[(String, BundleActivator)] = Seq(
    "blended.akka" -> new BlendedAkkaActivator(),
    "blended.jmx" -> new BlendedJmxActivator()
  )

  override def baseDir : String = new File(BlendedTestSupport.projectTestOutput, "container").getAbsolutePath()

  "The JMX Activator" - {

    "should expose the platform MBean Server and a BlendedMBeanServerFacade as a service" in {
      mandatoryService[MBeanServer](registry, None)
      mandatoryService[BlendedMBeanServerFacade](registry, None)
    }

    "should expose a ProductMBeanManager as a service" in {
      mandatoryService[ProductMBeanManager](registry, None)
    }
  }
}
