package blended.streams.dispatcher.internal.builder

import java.io.File
import java.util.UUID

import akka.stream.KillSwitch
import blended.activemq.brokerstarter.internal.BrokerActivator
import blended.jms.utils.{IdAwareConnectionFactory, JmsDestination, JmsQueue}
import blended.streams.BlendedStreamsConfig
import blended.streams.jms.{JmsProducerSettings, JmsStreamSupport}
import blended.streams.message.{FlowEnvelope, FlowMessage}
import blended.streams.processor.Collector
import blended.streams.transaction.internal.FileFlowTransactionManager
import blended.streams.transaction.{FlowTransaction, FlowTransactionEvent, FlowTransactionManager, FlowTransactionUpdate}
import blended.streams.worklist._
import blended.testsupport.pojosr.JmsConnectionHelper
import blended.testsupport.{BlendedTestSupport, RequiresForkedJVM}
import blended.util.RichTry._
import org.osgi.framework.BundleActivator
import org.scalatest.matchers.should.Matchers

import scala.concurrent.Await
import scala.concurrent.duration._

@RequiresForkedJVM
class TransactionOutboundSpec extends DispatcherSpecSupport
  with Matchers
  with JmsStreamSupport
  with JmsConnectionHelper {

  System.setProperty("testName", "trans")
  override def loggerName: String = "outbound"

  override protected def beforeAll(): Unit = {
    super.beforeAll()

    implicit val to : FiniteDuration = timeout
    val bs : DispatcherBuilderSupport = dispCtxt.bs

    val tMgr : FlowTransactionManager =
      FileFlowTransactionManager(new File(BlendedTestSupport.projectTestOutput, "transOutbound"))(dispCtxt.system)
    val streamsCfg : BlendedStreamsConfig = mandatoryService[BlendedStreamsConfig](registry)

    new TransactionOutbound(
      headerConfig = bs.headerConfig,
      tMgr = tMgr,
      dispatcherCfg = dispCtxt.cfg,
      internalCf = cf,
      streamsCfg = streamsCfg,
      log = dispCtxt.envLogger
    )(system = dispCtxt.system, bs = bs).build()
  }

  def transactionEnvelope(ctxt : DispatcherExecContext, event : FlowTransactionEvent) : FlowEnvelope = {
    FlowTransactionEvent.event2envelope(ctxt.bs.headerConfig)(event)
      .withHeader(ctxt.bs.headerEventVendor, "activemq").unwrap
      .withHeader(ctxt.bs.headerEventProvider, "activemq").unwrap
      .withHeader(ctxt.bs.headerEventDest, JmsDestination.create("cbeOut").unwrap.asString).unwrap
  }

  def sendTransactions(ctxt: DispatcherExecContext, cf : IdAwareConnectionFactory)(envelopes: FlowEnvelope*) : KillSwitch = {

    val pSettings : JmsProducerSettings = JmsProducerSettings(
      log = ctxt.envLogger,
      headerCfg = ctxt.bs.headerConfig,
      connectionFactory = cf,
      jmsDestination = Some(JmsQueue("internal.transactions"))
    )

    sendMessages(
      pSettings,
      log = ctxt.envLogger,
      envelopes: _*
    )(ctxt.system).unwrap
  }

  def receiveCbes: Collector[FlowEnvelope] = receiveMessages(
    headerCfg = dispCtxt.bs.headerConfig,
    cf = cf,
    dest = JmsQueue("cbeOut"),
    log = dispCtxt.envLogger,
    timeout = Some(timeout)
  )(dispCtxt.system)

  "The transaction outbound handler should" - {

    "do not send a cbe event if the FlowEnvelope doesn't have a CBE header" in {

      val envelopes = Seq(
        transactionEnvelope(dispCtxt, FlowTransaction.startEvent()),
      )

      val switch = sendTransactions(dispCtxt, cf)(envelopes:_*)
      val collector = receiveCbes

      val cbes = Await.result(collector.result, timeout + 1.second)
      cbes should have size 0
      switch.shutdown()
    }

    "send a cbe event if the FlowEnvelope does have a CBE header = true" in {

      val envelopes = Seq(
        transactionEnvelope(dispCtxt, FlowTransaction.startEvent()).withHeader(dispCtxt.bs.headerCbeEnabled, true).get
      )

      val switch = sendTransactions(dispCtxt, cf)(envelopes:_*)
      val collector = receiveCbes

      val cbes = Await.result(collector.result, timeout + 1.second)
      cbes should have size 1
      switch.shutdown()
    }

    "do not send a cbe event if the FlowEnvelope does have a CBE header = false" in {

      val envelopes = Seq(
        transactionEnvelope(dispCtxt, FlowTransaction.startEvent()).withHeader(dispCtxt.bs.headerCbeEnabled, false).get
      )

      val switch = sendTransactions(dispCtxt, cf)(envelopes:_*)
      val collector = receiveCbes

      val cbes = Await.result(collector.result, timeout + 1.second)
      cbes should have size 0
      switch.shutdown()
    }

    "do not send Cbes for transaction updates" in {

      val envStart = transactionEnvelope(dispCtxt, FlowTransactionUpdate(
        transactionId = UUID.randomUUID().toString(),
        properties = FlowMessage.noProps,
        updatedState = WorklistStateCompleted,
        branchIds = "foo, bar"
      )).withHeader(dispCtxt.bs.headerCbeEnabled, true).unwrap

      val switch = sendTransactions(dispCtxt, cf)(envStart)
      val collector = receiveCbes

      val cbes = Await.result(collector.result, timeout + 1.second)
      cbes should have size 0
      switch.shutdown()
    }
  }
}
