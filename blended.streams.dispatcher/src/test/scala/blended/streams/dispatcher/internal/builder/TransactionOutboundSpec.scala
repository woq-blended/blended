package blended.streams.dispatcher.internal.builder

import java.io.File
import java.util.UUID

import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, KillSwitch, Materializer}
import blended.activemq.brokerstarter.internal.BrokerActivator
import blended.jms.utils.{IdAwareConnectionFactory, JmsDestination, JmsQueue}
import blended.streams.jms.{JmsProducerSettings, JmsStreamSupport}
import blended.streams.message.{FlowEnvelope, FlowMessage}
import blended.streams.processor.Collector
import blended.streams.transaction.internal.FileFlowTransactionManager
import blended.streams.transaction.{FlowTransaction, FlowTransactionEvent, FlowTransactionManager, FlowTransactionUpdate}
import blended.streams.worklist._
import blended.testsupport.{BlendedTestSupport, RequiresForkedJVM}
import blended.util.logging.Logger
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

  private val tMgr : FlowTransactionManager = FileFlowTransactionManager(new File(BlendedTestSupport.projectTestOutput, "transOutbound"))

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
  }

  def sendTransactions(ctxt: DispatcherExecContext, cf : IdAwareConnectionFactory)(envelopes: FlowEnvelope*)
    (implicit system : ActorSystem, materializer : Materializer, eCtxt: ExecutionContext) : KillSwitch = {

    val pSettings : JmsProducerSettings = JmsProducerSettings(
      log = Logger(loggerName),
      headerCfg = ctxt.bs.headerConfig,
      connectionFactory = cf,
      jmsDestination = Some(JmsQueue("internal.transactions"))
    )

    sendMessages(
      pSettings,
      log = ctxt.bs.streamLogger,
      envelopes: _*
    ).get
  }

  def receiveCbes: Collector[FlowEnvelope] = receiveMessages(
    headerCfg = ctxt.bs.headerConfig,
    cf = cf,
    dest = JmsQueue("cbeOut"),
    Logger(loggerName)
  )

  "The transaction outbound handler should" - {

    "do not send a cbe event if the FlowEnvelope doesn't have a CBE header" in {

      val envelopes = Seq(
        transactionEnvelope(ctxt, FlowTransaction.startEvent()),
      )

      val switch = sendTransactions(ctxt, cf)(envelopes:_*)
      val collector = receiveCbes

      val cbes = Await.result(collector.result, timeout + 1.second)
      cbes should have size 0
      switch.shutdown()
    }

    "send a cbe event if the FlowEnvelope does have a CBE header = true" in {

      val envelopes = Seq(
        transactionEnvelope(ctxt, FlowTransaction.startEvent()).withHeader(ctxt.bs.headerCbeEnabled, true).get
      )

      val switch = sendTransactions(ctxt, cf)(envelopes:_*)
      val collector = receiveCbes

      val cbes = Await.result(collector.result, timeout + 1.second)
      cbes should have size 1
      switch.shutdown()
    }

    "do not send a cbe event if the FlowEnvelope does have a CBE header = false" in {

      val envelopes = Seq(
        transactionEnvelope(ctxt, FlowTransaction.startEvent()).withHeader(ctxt.bs.headerCbeEnabled, false).get
      )

      val switch = sendTransactions(ctxt, cf)(envelopes:_*)
      val collector = receiveCbes

      val cbes = Await.result(collector.result, timeout + 1.second)
      cbes should have size 0
      switch.shutdown()
    }

    "do not send Cbes for transaction updates" in {

      val envStart = transactionEnvelope(ctxt, FlowTransactionUpdate(
        transactionId = UUID.randomUUID().toString(),
        properties = FlowMessage.noProps,
        updatedState = WorklistStateCompleted,
        branchIds = "foo, bar"
      )).withHeader(ctxt.bs.headerCbeEnabled, true).get

      val switch = sendTransactions(ctxt, cf)(envStart)
      val collector = receiveCbes

      val cbes = Await.result(collector.result, timeout + 1.second)
      cbes should have size 0
      switch.shutdown()
    }
  }
}
