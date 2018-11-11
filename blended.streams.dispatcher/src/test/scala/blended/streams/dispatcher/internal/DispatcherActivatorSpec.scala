package blended.streams.dispatcher.internal

import java.io.File

import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, Materializer}
import blended.activemq.brokerstarter.BrokerActivator
import blended.akka.internal.BlendedAkkaActivator
import blended.jms.bridge.internal.BridgeActivator
import blended.jms.utils.JmsDestination
import blended.streams.dispatcher.internal.builder.DispatcherSpecSupport
import blended.streams.jms.JmsStreamSupport
import blended.streams.message.FlowEnvelope
import blended.testsupport.pojosr.PojoSrTestHelper
import blended.testsupport.{BlendedTestSupport, RequiresForkedJVM}
import blended.util.logging.Logger
import org.osgi.framework.BundleActivator
import org.scalatest.Matchers

import scala.concurrent.{Await, ExecutionContext}
import scala.concurrent.duration._

@RequiresForkedJVM
class DispatcherActivatorSpec extends DispatcherSpecSupport
  with Matchers
  with PojoSrTestHelper
  with JmsStreamSupport {

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

        implicit val system : ActorSystem = ctxt.system
        implicit val materializer : Materializer = ActorMaterializer()

        implicit val eCtxt : ExecutionContext = ctxt.system.dispatcher

        implicit val timeout : FiniteDuration = 5.seconds
        // make sure we can connect to all connection factories
        val amq = jmsConnectionFactory(registry, ctxt)("activemq", "activemq", timeout).get
        val sonic = jmsConnectionFactory(registry, ctxt)("sonic75", "central", timeout).get
        val ccQueue = jmsConnectionFactory(registry, ctxt)("sagum", s"${country}_queue", timeout).get

        val switch = sendMessages(
          headerCfg = ctxt.bs.headerConfig,
          cf = sonic,
          dest = JmsDestination.create("sonic.data.in").get,
          log = Logger(loggerName),
          msgs = FlowEnvelope().withHeader(ctxt.bs.headerResourceType, "Dummy").get
        )

        val errColl = receiveMessages(headerCfg = ctxt.bs.headerConfig, cf = sonic, dest = JmsDestination.create("global.error").get)
        val cbeColl = receiveMessages(headerCfg = ctxt.bs.headerConfig, cf = sonic, dest = JmsDestination.create("cc.global.evnt.out").get)

        try {
          val errors = Await.result(errColl.result, timeout + 1.second)
          val cbes = Await.result(cbeColl.result, timeout + 1.second)
          errors should have size 1
          cbes should have size 2
        } finally {
          switch.shutdown()
        }
      }
    }
  }
}
