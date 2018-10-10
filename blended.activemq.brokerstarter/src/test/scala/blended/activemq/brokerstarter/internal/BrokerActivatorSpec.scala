package blended.activemq.brokerstarter.internal

import java.io.File

import blended.activemq.brokerstarter.BrokerActivator
import blended.akka.internal.BlendedAkkaActivator
import blended.jms.utils.IdAwareConnectionFactory
import blended.testsupport.BlendedTestSupport
import blended.testsupport.pojosr.{PojoSrTestHelper, SimplePojosrBlendedContainer}
import blended.testsupport.scalatest.LoggingFreeSpec
import org.scalatest.Matchers

import scala.concurrent.duration._

class BrokerActivatorSpec extends LoggingFreeSpec
  with SimplePojosrBlendedContainer
  with PojoSrTestHelper
  with Matchers {

  private val baseDir = new File(BlendedTestSupport.projectTestOutput, "container").getAbsolutePath()

  "The BrokerActivator should" - {

    "start the configured brokers correctly" in {
      withSimpleBlendedContainer(baseDir) { sr =>
        withStartedBundles(sr)(Seq(
          "blended.akka" -> Some(() => new BlendedAkkaActivator()),
          "blended.activemq.brokerstarter" -> Some(() => new BrokerActivator())
        )) { sr =>

          implicit val timeout = 10.seconds
          waitOnService[IdAwareConnectionFactory](sr)(Some("(&(vendor=activemq)(provider=blended))")) should be (defined)
          waitOnService[IdAwareConnectionFactory](sr)(Some("(&(vendor=activemq)(provider=broker2))")) should be (defined)
        }
      }
    }
  }
}
