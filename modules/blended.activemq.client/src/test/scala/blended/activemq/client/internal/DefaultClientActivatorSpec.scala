package blended.activemq.client.internal

import java.io.File

import blended.akka.internal.BlendedAkkaActivator
import blended.container.context.api.ContainerContext
import blended.jms.utils.IdAwareConnectionFactory
import blended.testsupport.pojosr.{PojoSrTestHelper, SimplePojoContainerSpec}
import blended.testsupport.scalatest.LoggingFreeSpecLike
import blended.testsupport.{BlendedTestSupport, RequiresForkedJVM}
import org.osgi.framework.BundleActivator

@RequiresForkedJVM
class DefaultClientActivatorSpec extends SimplePojoContainerSpec
  with LoggingFreeSpecLike
  with PojoSrTestHelper {

  override def baseDir : String = new File(BlendedTestSupport.projectTestOutput, "default").getAbsolutePath()

  override def bundles : Seq[(String, BundleActivator)] = Seq(
    "blended.akka" -> new BlendedAkkaActivator(),
    "blended.activemq.client" -> new AmqClientActivator()
  )

  "The ActiveMQ Client Activator should" - {

    "create a Connection Factory for each configured client connection" in {
      mandatoryService[ContainerContext](registry)
      mandatoryService[IdAwareConnectionFactory](registry, filter = Some("(&(vendor=activemq)(provider=conn1))"))
      mandatoryService[IdAwareConnectionFactory](registry, filter = Some("(&(vendor=activemq)(provider=conn2))"))
    }
  }
}
