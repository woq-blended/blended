package blended.streams.dispatcher.internal.builder

import akka.stream._
import akka.stream.scaladsl.{Keep, Sink}
import blended.jms.utils.{IdAwareConnectionFactory, JmsDestination, JmsQueue}
import blended.streams.StreamFactories
import blended.streams.jms.JmsStreamSupport
import blended.streams.message.FlowEnvelope
import blended.streams.processor.Collector
import blended.streams.transaction.{FlowTransaction, FlowTransactionEvent}
import blended.testsupport.RequiresForkedJVM
import blended.testsupport.pojosr.PojoSrTestHelper
import org.scalatest.matchers.should.Matchers

import scala.concurrent.duration._
import scala.concurrent.Await

@RequiresForkedJVM
class CbeFlowSpec extends DispatcherSpecSupport
  with Matchers
  with PojoSrTestHelper
  with JmsStreamSupport {

  override def loggerName : String = "outbound"

  def transactionEnvelope(ctxt : DispatcherExecContext, event : FlowTransactionEvent) : FlowEnvelope = {
    FlowTransactionEvent.event2envelope(ctxt.bs.headerConfig)(event)
      .withHeader(ctxt.bs.headerEventVendor, "activemq").get
      .withHeader(ctxt.bs.headerEventProvider, "activemq").get
      .withHeader(ctxt.bs.headerEventDest, JmsDestination.create("cbeOut").get.asString).get
      .withHeader(ctxt.bs.headerConfig.headerTrack, false).get
  }

  private def cbeSendFlow = {
    new CbeSendFlow(
      headerConfig = dispCtxt.bs.headerConfig,
      dispatcherCfg = dispCtxt.cfg,
      internalCf =cf,
      streamLogger = dispCtxt.envLogger
    )(system = dispCtxt.system, bs = dispCtxt.bs).build()
  }

  def sendTransactions(
    ctxt : DispatcherExecContext,
    cf : IdAwareConnectionFactory
  )(envelopes : FlowEnvelope*) : KillSwitch = {

    val (actor, switch) = StreamFactories.actorSource(envelopes.size)
      .viaMat(KillSwitches.single)(Keep.both)
      .viaMat(cbeSendFlow)(Keep.left)
      .toMat(Sink.ignore)(Keep.left)
      .run()(materializer = ctxt.materializer)

    envelopes.foreach(e => actor ! e)

    switch
  }

  def receiveCbes : Collector[FlowEnvelope] = receiveMessages(
    headerCfg = dispCtxt.bs.headerConfig,
    cf = cf,
    dest = JmsQueue("cbeOut"),
    log = dispCtxt.envLogger,
    timeout = Some(timeout),
    ackTimeout = 1.second
  )(system = dispCtxt.system)

  "The CBE Flow should" - {

    "Generate a CBE event (started) for a transaction started event" in {

      val started = FlowTransaction.startEvent()

      val switch = sendTransactions(ctxt = dispCtxt, cf = cf)(transactionEnvelope(dispCtxt, started))
      val cbeColl = receiveCbes

      val cbes = Await.result(cbeColl.result, timeout + 1.second)
      cbes should have size 1

      val t = FlowTransaction.envelope2Transaction(dispCtxt.bs.headerConfig)(cbes.head)
      t.state should be(started.state)
      t.tid should be(started.transactionId)

      switch.shutdown()
    }
  }
}
