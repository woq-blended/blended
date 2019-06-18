package blended.activemq.client.internal

import java.io.File

import blended.akka.internal.BlendedAkkaActivator
import blended.container.context.api.ContainerIdentifierService
import blended.jms.utils.IdAwareConnectionFactory
import blended.testsupport.BlendedTestSupport
import blended.testsupport.pojosr.{PojoSrTestHelper, SimplePojoContainerSpec}
import blended.testsupport.scalatest.LoggingFreeSpecLike
import org.osgi.framework.BundleActivator

import scala.concurrent.duration._

class DefaultClientActivatorSpec extends SimplePojoContainerSpec
  with LoggingFreeSpecLike
  with PojoSrTestHelper {

  override def baseDir : String = new File(BlendedTestSupport.projectTestOutput, "default").getAbsolutePath()

  override def bundles : Seq[(String, BundleActivator)] = Seq(
    "blended.akka" -> new BlendedAkkaActivator(),
    "blended.activemq.client" -> new AmqClientActivator()
  )

  private implicit val timeout : FiniteDuration = 3.seconds
  mandatoryService[ContainerIdentifierService](registry)(None)

  "The ActiveMQ Client Activator should" - {

    "create a Connection Factory for each configured client connection" in {
      mandatoryService[IdAwareConnectionFactory](registry)(Some("(&(vendor=activemq)(provider=conn1))"))
      mandatoryService[IdAwareConnectionFactory](registry)(Some("(&(vendor=activemq)(provider=conn2))"))
    }
  }
}
