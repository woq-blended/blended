package blended.streams.dispatcher.internal

import java.io.File

import akka.actor.ActorSystem
import akka.stream.scaladsl.{Flow, Keep, Sink}
import akka.stream.{ActorMaterializer, KillSwitch, Materializer}
import blended.activemq.brokerstarter.internal.BrokerActivator
import blended.akka.internal.BlendedAkkaActivator
import blended.jms.bridge.internal.BridgeActivator
import blended.jms.utils.{IdAwareConnectionFactory, JmsDestination, JmsQueue}
import blended.streams.dispatcher.internal.builder.DispatcherSpecSupport
import blended.streams.jms.{JmsProducerSettings, JmsStreamSupport}
import blended.streams.message.FlowEnvelope
import blended.streams.testsupport.LoggingEventAppender
import blended.streams.transaction._
import blended.testsupport.pojosr.PojoSrTestHelper
import blended.testsupport.{BlendedTestSupport, RequiresForkedJVM}
import ch.qos.logback.classic.Level
import ch.qos.logback.classic.spi.ILoggingEvent
import org.osgi.framework.BundleActivator
import org.scalatest.matchers.should.Matchers
import blended.util.RichTry._

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}
import blended.streams.internal.BlendedStreamsActivator

@RequiresForkedJVM
class DispatcherActivatorSpec extends DispatcherSpecSupport
  with Matchers
  with PojoSrTestHelper
  with JmsStreamSupport {

  System.setProperty("AppCountry", country)
  System.setProperty("AppLocation", location)

  implicit val timeout : FiniteDuration = 5.seconds

  override def loggerName : String = classOf[DispatcherActivatorSpec].getName()
  override def baseDir : String = new File(BlendedTestSupport.projectTestOutput, "container").getAbsolutePath()

  override def bundles : Seq[(String, BundleActivator)] = Seq(
    "blended.akka" -> new BlendedAkkaActivator(),
    "blended.activemq.brokerstarter" -> new BrokerActivator(),
    "blended.jms.bridge" -> new BridgeActivator(),
    "blended.streams" -> new BlendedStreamsActivator(),
    "blended.streams.dispatcher" -> new DispatcherActivator()
  )

  private implicit val system : ActorSystem = mandatoryService[ActorSystem](registry)(None)
  private implicit val materializer : Materializer = ActorMaterializer()
  private implicit val eCtxt : ExecutionContext = system.dispatcher

  private val ctxt : DispatcherExecContext = createDispatcherExecContext()

  // make sure we can connect to all connection factories
  private val amq = jmsConnectionFactory(registry, ctxt)("activemq", "activemq", timeout).unwrap
  private val sonic = jmsConnectionFactory(registry, ctxt)("sonic75", "central", timeout).unwrap
  private val ccQueue = jmsConnectionFactory(registry, ctxt)("sagum", s"${country}_queue", timeout).unwrap

  private def getResults(cf : IdAwareConnectionFactory, dest : JmsDestination*) : Seq[List[FlowEnvelope]] = {

    val collectors = dest.map { d =>
      receiveMessages(
        headerCfg = ctxt.bs.headerConfig,
        cf = cf,
        dest = d,
        log = ctxt.envLogger,
        timeout = Some(timeout)
      )
    }

    Await.result(Future.sequence(collectors.map(_.result)), timeout + 1.second)
  }

  "The activated dispatcher should" - {

    "process inbound messages with a wrong ResourceType" in {

      val logSink = Flow[ILoggingEvent]
        .filter{ event =>
          event.getLevel() == Level.INFO || event.getLevel() == Level.WARN
        }
        .toMat(Sink.seq[ILoggingEvent])(Keep.right)

      val appender = new LoggingEventAppender[Future[Seq[ILoggingEvent]]]("App.transactions")
      val logEventsFut = appender.attachAndStart(logSink)

      val env : FlowEnvelope = FlowEnvelope().withHeader(ctxt.bs.headerResourceType, "Dummy").unwrap

      val pSettings : JmsProducerSettings = JmsProducerSettings(
        log = ctxt.envLogger,
        headerCfg = ctxt.bs.headerConfig,
        connectionFactory = sonic,
        jmsDestination = Some(JmsQueue("sonic.data.in"))
      )

      val switch : KillSwitch = sendMessages(pSettings, ctxt.envLogger, env).unwrap

      val results = getResults(
        cf = sonic,
        JmsDestination.create("global.error").get,
        JmsDestination.create("cc.global.evnt.out").get
      )

      appender.stop()
      val logEvents = Await.result(logEventsFut, timeout + 1.second).filter(_.getMessage.contains(env.id))

      val errors : List[FlowEnvelope] = results.head
      val cbes : List[FlowEnvelope] = results.last

      // TODO: Reconstruct FlowTransaction from String ??
      assert(logEvents.size == 2)
      assert(logEvents.forall { e => e.getMessage().startsWith("FlowTransaction") })
      assert(logEvents.count(e => e.getMessage().startsWith(s"FlowTransaction[$FlowTransactionStateStarted]")) == 1)
      assert(logEvents.count(e => e.getMessage().startsWith(s"FlowTransaction[$FlowTransactionStateFailed]")) == 1)

      errors should have size 1
      cbes should have size 0

      switch.shutdown()
    }

    "process inbound messages with a correct ResourceType" in {

      val logSink = Flow[ILoggingEvent]
        .filter { event =>
          event.getLevel() == Level.INFO
        }
        .toMat(Sink.seq[ILoggingEvent])(Keep.right)

      val appender = new LoggingEventAppender[Future[Seq[ILoggingEvent]]]("App.transactions")
      val logEventsFut = appender.attachAndStart(logSink)

      val env : FlowEnvelope = FlowEnvelope().withHeader(ctxt.bs.headerResourceType, "NoCbe").unwrap

      val pSettings : JmsProducerSettings = JmsProducerSettings(
        log = ctxt.envLogger,
        headerCfg = ctxt.bs.headerConfig,
        connectionFactory = sonic,
        jmsDestination = Some(JmsQueue("sonic.data.in"))
      )

      val switch : KillSwitch = sendMessages(pSettings, ctxt.envLogger, env).unwrap

      val results = getResults(
        cf = sonic,
        JmsQueue("global.error"),
        JmsQueue("cc.nocbe")
      )

      appender.stop()
      val logEvents = Await.result(logEventsFut, timeout + 1.second).filter(_.getMessage.contains(env.id))

      val errors : List[FlowEnvelope] = results.head
      val cbes : List[FlowEnvelope] = results.last

      // TODO: Reconstruct FlowTransaction from String ??
      assert(logEvents.size == 2)
      assert(logEvents.forall { e => e.getMessage().startsWith("FlowTransaction") })
      assert(logEvents.count(e => e.getMessage().startsWith(s"FlowTransaction[$FlowTransactionStateStarted]")) == 1)
      assert(logEvents.count(e => e.getMessage().startsWith(s"FlowTransaction[$FlowTransactionStateCompleted]")) == 1)

      errors should have size 0
      cbes should have size 1

      switch.shutdown()
    }

    "send configured startup messages" in {
      val results = getResults(
        cf = sonic,
        JmsQueue("global.error"),
        JmsQueue("startup")
      )

      val errors : List[FlowEnvelope] = results.head
      val out : List[FlowEnvelope] = results.last

      errors should be (empty)
      out should have size(1)

      out.head.header[String](ctxt.bs.headerConfig.headerResourceType) should be (Some("DispatcherStarted"))
      out.head.flowMessage.body() should be (s"$country;${location.substring(1)}")
    }
  }
}
