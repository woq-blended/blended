package blended.streams.dispatcher.internal.builder

import java.util.UUID

import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, KillSwitch, Materializer}
import blended.activemq.brokerstarter.internal.BrokerActivator
import blended.jms.utils.{IdAwareConnectionFactory, JmsDestination, JmsQueue}
import blended.streams.jms.JmsStreamSupport
import blended.streams.message.{FlowEnvelope, FlowMessage}
import blended.streams.processor.Collector
import blended.streams.transaction.{FlowTransaction, FlowTransactionEvent, FlowTransactionManager, FlowTransactionUpdate}
import blended.streams.worklist.WorklistState
import blended.testsupport.RequiresForkedJVM
import org.osgi.framework.BundleActivator
import org.scalatest.{BeforeAndAfterAll, Matchers}

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext}
import scala.reflect.ClassTag

@RequiresForkedJVM
class TransactionOutboundSpec extends DispatcherSpecSupport
  with Matchers
  with JmsStreamSupport
  with BeforeAndAfterAll {

  private implicit val timeout : FiniteDuration = 3.seconds

  System.setProperty("testName", "trans")
  override def loggerName: String = "outbound"

  override def bundles: Seq[(String, BundleActivator)] = super.bundles ++ Seq(
    "blended.activemq.brokerstarter" -> new BrokerActivator()
  )

  private implicit val system : ActorSystem = mandatoryService[ActorSystem](registry)(None)(
    clazz = ClassTag(classOf[ActorSystem]),
    timeout = timeout
  )

  private implicit val materializer : Materializer = ActorMaterializer()
  private implicit val eCtxt : ExecutionContext = system.dispatcher

  private val ctxt = createDispatcherExecContext()

  val (internalVendor, internalProvider) = ctxt.cfg.providerRegistry.internalProvider.map(p => (p.vendor, p.provider)).get
  private val cf = jmsConnectionFactory(registry, ctxt)(internalVendor, internalProvider, 3.seconds).get

  private val tMgr = system.actorOf(FlowTransactionManager.props())

  override protected def beforeAll(): Unit = {
    implicit val bs : DispatcherBuilderSupport = ctxt.bs
    new TransactionOutbound(
      headerConfig = ctxt.bs.headerConfig,
      tMgr = tMgr,
      dispatcherCfg = ctxt.cfg,
      internalCf = cf,
      ctxt.bs.streamLogger
    ).build()
  }

  def transactionEnvelope(ctxt : DispatcherExecContext, event : FlowTransactionEvent) : FlowEnvelope = {
    FlowTransactionEvent.event2envelope(ctxt.bs.headerConfig)(event)
      .withHeader(ctxt.bs.headerEventVendor, "activemq").get
      .withHeader(ctxt.bs.headerEventProvider, "activemq").get
      .withHeader(ctxt.bs.headerEventDest, JmsDestination.create("cbeOut").get.asString).get
      .withHeader(ctxt.bs.headerConfig.headerTrack, false).get
  }

  def sendTransactions(ctxt: DispatcherExecContext, cf : IdAwareConnectionFactory)(envelopes: FlowEnvelope*)
    (implicit system : ActorSystem, materializer : Materializer, eCtxt: ExecutionContext) : KillSwitch = {
    sendMessages(
      headerCfg = ctxt.bs.headerConfig,
      cf = cf,
      dest = JmsQueue("internal.transactions"),
      log = ctxt.bs.streamLogger,
      envelopes: _*
    )
  }

  def receiveCbes: Collector[FlowEnvelope] = receiveMessages(
    headerCfg = ctxt.bs.headerConfig,
    cf = cf,
    dest = JmsQueue("cbeOut")
  )

  "The transaction outbound handler should" - {

    "send a cbe event if the FlowEnvelope has CBE enabled" in {

      val envelopes = Seq(
        transactionEnvelope(ctxt, FlowTransaction.startEvent()),
        transactionEnvelope(ctxt, FlowTransaction.startEvent()).withHeader(ctxt.bs.headerCbeEnabled, false).get
      )

      val switch = sendTransactions(ctxt, cf)(envelopes:_*)
      val collector = receiveCbes

      val cbes = Await.result(collector.result, timeout + 1.second)
      cbes should have size 1
      switch.shutdown()
    }

    "do not send Cbes for transaction updates" in {
      val envelopes = Seq(
        transactionEnvelope(ctxt, FlowTransactionUpdate(
          transactionId = UUID.randomUUID().toString(),
          properties = FlowMessage.noProps,
          updatedState = WorklistState.Started,
          branchIds = "foo"
        ))
      )

      val switch = sendTransactions(ctxt, cf)(envelopes:_*)
      val collector = receiveCbes

      val cbes = Await.result(collector.result, timeout + 1.second)
      cbes should have size 0
      switch.shutdown()
    }
  }
}
