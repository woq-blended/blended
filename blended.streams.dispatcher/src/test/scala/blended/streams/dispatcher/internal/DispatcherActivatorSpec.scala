package blended.streams.dispatcher.internal

import java.io.File

import blended.activemq.brokerstarter.BrokerActivator
import blended.akka.internal.BlendedAkkaActivator
import blended.jms.bridge.internal.BridgeActivator
import blended.jms.utils.IdAwareConnectionFactory
import blended.testsupport.{BlendedTestSupport, RequiresForkedJVM}
import blended.testsupport.pojosr.{BlendedPojoRegistry, PojoSrTestHelper, SimplePojoContainerSpec}
import org.osgi.framework.BundleActivator
import org.scalatest.Matchers

import scala.concurrent.duration._

@RequiresForkedJVM
class DispatcherActivatorSpec extends SimplePojoContainerSpec
  with Matchers
  with PojoSrTestHelper {

  System.setProperty("AppCountry", "cc")
  System.setProperty("AppLocation", "09999")

  override def baseDir: String = new File(BlendedTestSupport.projectTestOutput, "container").getAbsolutePath()

  override def bundles: Seq[(String, BundleActivator)] = Seq(
    "blended.akka" -> new BlendedAkkaActivator(),
    "blended.activemq.brokerstarter" -> new BrokerActivator(),
    "blended.jms.bridge" -> new BridgeActivator(),
    "blended.streams.dispatcher" -> new DispatcherActivator()
  )

  private[this] def withDispatcher[T](f : () => T) : T = {
    f()
  }

  "The activated dispatcher should" - {

    "create the dispatcher" in {

      withDispatcher { () =>

        implicit val timeout = 3.seconds
        val xx = mandatoryService[IdAwareConnectionFactory](registry)(None)
        pending
      }
    }
  }
}
