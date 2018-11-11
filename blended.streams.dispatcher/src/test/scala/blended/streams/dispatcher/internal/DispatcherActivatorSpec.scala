package blended.streams.dispatcher.internal

import java.io.File

import blended.activemq.brokerstarter.BrokerActivator
import blended.akka.internal.BlendedAkkaActivator
import blended.jms.bridge.internal.BridgeActivator
import blended.streams.dispatcher.internal.builder.DispatcherSpecSupport
import blended.testsupport.pojosr.PojoSrTestHelper
import blended.testsupport.{BlendedTestSupport, RequiresForkedJVM}
import org.osgi.framework.BundleActivator
import org.scalatest.Matchers

import scala.concurrent.duration._

@RequiresForkedJVM
class DispatcherActivatorSpec extends DispatcherSpecSupport
  with Matchers
  with PojoSrTestHelper {

  System.setProperty("AppCountry", country)
  System.setProperty("AppLocation", location)

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

        implicit val eCtxt = ctxt.system.dispatcher

        implicit val timeout = 3.seconds
        // make sure we can connect to all connection factories
        val amq = jmsConnectionFactory(registry, ctxt)("activemq", "activemq", timeout)
        val sonic = jmsConnectionFactory(registry, ctxt)("sonic75", "central", timeout)
        val ccQueue = jmsConnectionFactory(registry, ctxt)("sagum", s"${country}_queue", timeout)

        pending
      }
    }
  }
}
