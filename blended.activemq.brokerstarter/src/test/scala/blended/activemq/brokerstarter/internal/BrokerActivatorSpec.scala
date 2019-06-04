package blended.activemq.brokerstarter.internal

import java.io.File

import blended.akka.internal.BlendedAkkaActivator
import blended.jms.utils.IdAwareConnectionFactory
import blended.testsupport.{BlendedTestSupport, RequiresForkedJVM}
import blended.testsupport.pojosr.{PojoSrTestHelper, SimplePojoContainerSpec}
import blended.testsupport.scalatest.LoggingFreeSpecLike
import org.osgi.framework.BundleActivator
import org.scalatest.Matchers

import scala.concurrent.duration._

@RequiresForkedJVM
class BrokerActivatorSpec extends SimplePojoContainerSpec
  with LoggingFreeSpecLike
  with PojoSrTestHelper
  with Matchers {

  override def baseDir : String = new File(BlendedTestSupport.projectTestOutput, "container").getAbsolutePath()

  override def bundles : Seq[(String, BundleActivator)] = Seq(
    "blended.akka" -> new BlendedAkkaActivator(),
    "blended.activemq.brokerstarter" -> new BrokerActivator()
  )

  "The BrokerActivator should" - {

    "start the configured brokers correctly" in {
      implicit val timeout = 10.seconds
      waitOnService[IdAwareConnectionFactory](registry)(Some("(&(vendor=activemq)(provider=blended))")) should be(defined)
      waitOnService[IdAwareConnectionFactory](registry)(Some("(&(vendor=activemq)(provider=broker2))")) should be(defined)
    }
  }

}
