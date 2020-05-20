package blended.akka.http.jmsqueue

import java.io.File

import blended.activemq.brokerstarter.internal.BrokerActivator
import blended.akka.http.HttpContext
import blended.akka.http.internal.BlendedAkkaHttpActivator
import blended.akka.internal.BlendedAkkaActivator
import blended.testsupport.BlendedTestSupport
import blended.testsupport.pojosr.{PojoSrTestHelper, SimplePojoContainerSpec}
import blended.testsupport.scalatest.LoggingFreeSpecLike
import org.osgi.framework.BundleActivator

import scala.concurrent.duration._

class JmsQueueActivatorSpec extends SimplePojoContainerSpec
  with LoggingFreeSpecLike
  with PojoSrTestHelper {

  override def baseDir : String = new File(BlendedTestSupport.projectTestOutput, "container").getAbsolutePath()

  override def bundles : Seq[(String, BundleActivator)] = Seq(
    "blended.akka" -> new BlendedAkkaActivator(),
    "blended.activemq.brokerstarter" -> new BrokerActivator(),
    "blended.akka.http" -> new BlendedAkkaHttpActivator(),
    "blended.akka.http.jmsqueue" -> new BlendedAkkaHttpJmsqueueActivator()
  )

  "The JmsQueueActivator" - {

    "should register a webcontext for the configured destinations" in {

      implicit val timeout : FiniteDuration = 3.seconds
      mandatoryService[HttpContext](registry)
    }
  }
}
