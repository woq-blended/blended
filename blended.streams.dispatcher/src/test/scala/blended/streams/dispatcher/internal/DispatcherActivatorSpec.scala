package blended.streams.dispatcher.internal

import java.io.File

import akka.actor.Scheduler
import akka.stream.scaladsl.{Flow, Keep, Sink}
import akka.stream.KillSwitch
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
import blended.testsupport.retry.Retry

@RequiresForkedJVM
class DispatcherActivatorSpec extends DispatcherSpecSupport
  with Matchers
  with PojoSrTestHelper
  with JmsStreamSupport {

  System.setProperty("AppCountry", country)
  System.setProperty("AppLocation", location)

  override def loggerName : String = classOf[DispatcherActivatorSpec].getName()
  override def baseDir : String = new File(BlendedTestSupport.projectTestOutput, "container").getAbsolutePath()

  override def bundles : Seq[(String, BundleActivator)] = Seq(
    "blended.akka" -> new BlendedAkkaActivator(),
    "blended.activemq.brokerstarter" -> new BrokerActivator(),
    "blended.jms.bridge" -> new BridgeActivator(),
    "blended.streams" -> new BlendedStreamsActivator(),
    "blended.streams.dispatcher" -> new DispatcherActivator()
  )

  private def getResults(cf : IdAwareConnectionFactory, dest : JmsDestination*) : Seq[List[FlowEnvelope]] = {

    implicit val eCtxt : ExecutionContext = dispCtxt.execCtxt

    val collectors = dest.map { d =>
      receiveMessages(
        headerCfg = dispCtxt.bs.headerConfig,
        cf = cf,
        dest = d,
        log = dispCtxt.envLogger,
        timeout = Some(timeout),
        ackTimeout = 1.second
      )(dispCtxt.system)
    }

    Await.result(Future.sequence(collectors.map(_.result)), timeout + 1.second)
  }

  "The activated dispatcher should" - {

    "process inbound messages with a wrong ResourceType" in logException {

      // make sure we can connect to all connection factories
      namedJmsConnectionFactory(registry, mustConnect = true, timeout = timeout)("activemq", "activemq").unwrap
      val sonic = namedJmsConnectionFactory(registry, mustConnect = true, timeout = timeout)("sonic75", "central").unwrap
      namedJmsConnectionFactory(registry, mustConnect = true, timeout = timeout)("sagum", s"${country}_queue").unwrap

      implicit val eCtxt : ExecutionContext = dispCtxt.system.dispatcher
      implicit val sched : Scheduler = dispCtxt.system.scheduler

      val logSink = Flow[ILoggingEvent]
        .filter{ event =>
          event.getLevel() == Level.INFO || event.getLevel() == Level.WARN
        }
        .toMat(Sink.seq[ILoggingEvent])(Keep.right)

      val appender = new LoggingEventAppender[Future[Seq[ILoggingEvent]]](dispCtxt.system)("App.transactions")
      val logEventsFut = appender.attachAndStart(logSink)

      val env : FlowEnvelope = FlowEnvelope().withHeader(dispCtxt.bs.headerResourceType, "Dummy").unwrap

      val pSettings : JmsProducerSettings = JmsProducerSettings(
        log = dispCtxt.envLogger,
        headerCfg = dispCtxt.bs.headerConfig,
        connectionFactory = sonic,
        jmsDestination = Some(JmsQueue("sonic.data.in"))
      )

      val switch : KillSwitch =
        sendMessages(pSettings, dispCtxt.envLogger, 1.second, env)(dispCtxt.system).unwrap

      val results = getResults(
        cf = sonic,
        JmsDestination.create("global.error").get,
        JmsDestination.create("cc.global.evnt.out").get
      )

      appender.stop()
      val logEvents = Await.result(logEventsFut, timeout + 1.second).filter(_.getMessage.contains(env.id))
      Retry.retry(100.millis, 10){
        assert(appender.started.get)
      }

      val errors : List[FlowEnvelope] = results.head
      val cbes : List[FlowEnvelope] = results.last

      // TODO: Reconstruct FlowTransaction from String ??
      dispCtxt.envLogger.underlying.info(s"Collected [${logEvents.size}] transaction log events")
      assert(logEvents.size >= 2)
      assert(logEvents.forall { e => e.getMessage().startsWith("FlowTransaction") })
      assert(logEvents.count(e => e.getMessage().startsWith(s"FlowTransaction[$FlowTransactionStateStarted]")) >= 1)
      assert(logEvents.count(e => e.getMessage().startsWith(s"FlowTransaction[$FlowTransactionStateFailed]")) >= 1)

      errors should have size 1
      cbes should have size 0

      switch.shutdown()
    }

    "process inbound messages with a correct ResourceType" in logException {

      // make sure we can connect to all connection factories
      namedJmsConnectionFactory(registry, mustConnect = true, timeout = timeout)("activemq", "activemq").unwrap
      val sonic = namedJmsConnectionFactory(registry, mustConnect = true, timeout = timeout)("sonic75", "central").unwrap
      namedJmsConnectionFactory(registry, mustConnect = true, timeout = timeout)("sagum", s"${country}_queue").unwrap

      implicit val eCtxt : ExecutionContext = dispCtxt.system.dispatcher
      implicit val sched : Scheduler = dispCtxt.system.scheduler

      val logSink = Flow[ILoggingEvent]
        .filter { event =>
          event.getLevel() == Level.INFO
        }
        .toMat(Sink.seq[ILoggingEvent])(Keep.right)

      val appender =
        new LoggingEventAppender[Future[Seq[ILoggingEvent]]](dispCtxt.system)("App.transactions")

      val logEventsFut = appender.attachAndStart(logSink)
      // We need to wait until the appender has properly attached
      Retry.retry(100.millis, 10){
        assert(appender.started.get)
      }

      val env : FlowEnvelope = FlowEnvelope().withHeader(dispCtxt.bs.headerResourceType, "NoCbe").unwrap

      val pSettings : JmsProducerSettings = JmsProducerSettings(
        log = dispCtxt.envLogger,
        headerCfg = dispCtxt.bs.headerConfig,
        connectionFactory = sonic,
        jmsDestination = Some(JmsQueue("sonic.data.in"))
      )

      val switch : KillSwitch = sendMessages(pSettings, dispCtxt.envLogger, 1.second, env)(dispCtxt.system).unwrap

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
      dispCtxt.envLogger.underlying.info(s"Collected [${logEvents.size}] transaction log events")
      assert(logEvents.size >= 2)
      assert(logEvents.forall { e => e.getMessage().startsWith("FlowTransaction") })
      assert(logEvents.count(e => e.getMessage().startsWith(s"FlowTransaction[$FlowTransactionStateStarted]")) >= 1)
      assert(logEvents.count(e => e.getMessage().startsWith(s"FlowTransaction[$FlowTransactionStateCompleted]")) >= 1)

      errors should have size 0
      cbes should have size 1

      switch.shutdown()
    }

    "send configured startup messages" in logException {

      // make sure we can connect to all connection factories
      namedJmsConnectionFactory(registry, mustConnect = true, timeout = timeout)("activemq", "activemq").unwrap
      val sonic = namedJmsConnectionFactory(registry, mustConnect = true, timeout = timeout)("sonic75", "central").unwrap
      namedJmsConnectionFactory(registry, mustConnect = true, timeout = timeout)("sagum", s"${country}_queue").unwrap

      val results = getResults(
        cf = sonic,
        JmsQueue("global.error"),
        JmsQueue("startup")
      )

      val errors : List[FlowEnvelope] = results.head
      val out : List[FlowEnvelope] = results.last

      errors should be (empty)
      out should not be (empty)

      out.head.header[String](dispCtxt.bs.headerConfig.headerResourceType) should be (Some("DispatcherStarted"))
      out.head.flowMessage.body() should be (s"$country;${location.substring(1)}")
    }
  }
}
