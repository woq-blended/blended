package blended.streams.dispatcher.internal.builder

import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, Materializer}
import blended.activemq.brokerstarter.BrokerActivator
import blended.jms.utils.{IdAwareConnectionFactory, JmsQueue}
import blended.streams.jms.JmsStreamSupport
import blended.streams.transaction.internal.FlowTransactionManager
import blended.streams.transaction.{FlowTransaction, FlowTransactionEvent}
import blended.testsupport.scalatest.LoggingFreeSpec
import org.scalatest.Matchers

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext}

class TransactionOutboundSpec extends LoggingFreeSpec
  with Matchers
  with DispatcherSpecSupport
  with JmsStreamSupport {

  System.setProperty("testName", "trans")

  override def loggerName: String = "outbound"

  "The transaction outbound handler should" - {

    "send a cbe event if the FlowEnvelope has CBE enabled" in {

      withDispatcherConfig { sr => ctxt =>
        withStartedBundles(sr)(Seq(
          "blended.activemq.brokerstarter" -> Some(() => new BrokerActivator())
        )) { sr =>

          implicit val timeout = 3.seconds
          implicit val system: ActorSystem = mandatoryService[ActorSystem](sr)(None)
          implicit val eCtxt: ExecutionContext = system.dispatcher
          implicit val materializer: Materializer = ActorMaterializer()
          implicit val bs: DispatcherBuilderSupport = ctxt.bs

          val (vendor, provider) = ctxt.cfg.providerRegistry.internalProvider.map(p => (p.vendor, p.provider)).get
          val cf: IdAwareConnectionFactory = jmsConnectionFactory(sr, ctxt)(vendor, provider, 10.seconds).get

          val tMgr = system.actorOf(FlowTransactionManager.props())

          new TransactionOutbound(
            headerConfig = ctxt.bs.headerConfig,
            tMgr = tMgr,
            registry = ctxt.cfg.providerRegistry,
            internalCf = cf,
            ctxt.bs.streamLogger
          ).build()

          val envelopes = Seq(
            FlowTransactionEvent.event2envelope(ctxt.bs.headerConfig)(FlowTransaction.startEvent()),
            FlowTransactionEvent.event2envelope(ctxt.bs.headerConfig)(FlowTransaction.startEvent()).withHeader(ctxt.bs.headerCbeEnabled, false).get
          )

          val switch = sendMessages(
            headerCfg = ctxt.bs.headerConfig,
            cf = cf,
            dest = JmsQueue("internal.transactions"),
            log = ctxt.bs.streamLogger,
            envelopes:_*
          )

          val collector = receiveMessages(
            headerCfg = ctxt.bs.headerConfig,
            cf = cf,
            dest = JmsQueue("internal.cbes")
          )

          val cbes = Await.result(collector.result, timeout + 1.second)
          cbes should have size 1
          switch.shutdown()
        }
      }
    }
  }
}
