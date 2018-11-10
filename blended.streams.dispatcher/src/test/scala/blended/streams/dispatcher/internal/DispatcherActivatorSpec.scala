package blended.streams.dispatcher.internal

import java.io.File

import blended.activemq.brokerstarter.BrokerActivator
import blended.akka.internal.BlendedAkkaActivator
import blended.jms.bridge.internal.BridgeActivator
import blended.jms.utils.IdAwareConnectionFactory
import blended.streams.dispatcher.internal.builder.DispatcherSpecSupport
import blended.testsupport.pojosr.{PojoSrTestHelper, SimplePojoContainerSpec}
import blended.testsupport.{BlendedTestSupport, RequiresForkedJVM}
import org.osgi.framework.BundleActivator
import org.scalatest.Matchers

import scala.concurrent.duration._

@RequiresForkedJVM
class DispatcherActivatorSpec extends DispatcherSpecSupport
  with Matchers
  with PojoSrTestHelper {


  System.setProperty("AppCountry", "cc")
  System.setProperty("AppLocation", "09999")

  override def loggerName: String = classOf[DispatcherActivatorSpec].getName()

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

      withDispatcherConfig { ctxt =>

        implicit val timeout = 3.seconds
        val cf = jmsConnectionFactory(registry, ctxt)("activemq", "activemq", 3.seconds)
        pending
      }
    }
  }
}
