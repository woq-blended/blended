package blended.streams.dispatcher.internal.builder

import akka.actor.ActorSystem
import akka.stream.scaladsl.{Keep, Sink, Source}
import akka.stream._
import blended.activemq.brokerstarter.BrokerActivator
import blended.jms.utils.{IdAwareConnectionFactory, JmsDestination, JmsQueue}
import blended.streams.jms.JmsStreamSupport
import blended.streams.message.FlowEnvelope
import blended.streams.processor.Collector
import blended.streams.transaction.{FlowTransaction, FlowTransactionEvent}
import blended.testsupport.RequiresForkedJVM
import blended.testsupport.pojosr.PojoSrTestHelper
import blended.util.logging.Logger
import org.osgi.framework.BundleActivator
import org.scalatest.Matchers

import scala.concurrent.{Await, ExecutionContext}
import scala.concurrent.duration._

@RequiresForkedJVM
class CbeFlowSpec extends DispatcherSpecSupport
  with Matchers
  with PojoSrTestHelper
  with JmsStreamSupport {

  private implicit val timeout = 3.seconds
  override def loggerName: String = "outbound"

  override def bundles: Seq[(String, BundleActivator)] = super.bundles ++ Seq(
    "blended.activemq.brokerstarter" -> new BrokerActivator()
  )

  private implicit val system : ActorSystem = mandatoryService[ActorSystem](registry)(None)
  private implicit val materializer : Materializer = ActorMaterializer()
  private implicit val eCtxt : ExecutionContext = system.dispatcher
  private val ctxt = createDispatcherExecContext()

  val (internalVendor, internalProvider) = ctxt.cfg.providerRegistry.internalProvider.map(p => (p.vendor, p.provider)).get
  private val cf = jmsConnectionFactory(registry, ctxt)(internalVendor, internalProvider, 3.seconds).get

  def transactionEnvelope(ctxt : DispatcherExecContext, event : FlowTransactionEvent) : FlowEnvelope = {
    FlowTransactionEvent.event2envelope(ctxt.bs.headerConfig)(event)
      .withHeader(ctxt.bs.headerEventVendor, "activemq").get
      .withHeader(ctxt.bs.headerEventProvider, "activemq").get
      .withHeader(ctxt.bs.headerEventDest, JmsDestination.create("cbeOut").get.asString).get
      .withHeader(ctxt.bs.headerConfig.headerTrack, false).get
  }

  private val cbeSendFlow = {
    implicit val bs : DispatcherBuilderSupport = ctxt.bs
    new CbeSendFlow(
      headerConfig = ctxt.bs.headerConfig,
      dispatcherCfg = ctxt.cfg,
      internalCf =cf,
      log = Logger("spec.cbesend")
    ).build()
  }

  def sendTransactions(ctxt: DispatcherExecContext, cf : IdAwareConnectionFactory)(envelopes: FlowEnvelope*)
    (implicit system : ActorSystem, materializer : Materializer, eCtxt: ExecutionContext) : KillSwitch = {

    val (actor, switch) = Source.actorRef(envelopes.size, OverflowStrategy.fail)
      .viaMat(KillSwitches.single)(Keep.both)
      .viaMat(cbeSendFlow)(Keep.left)
      .toMat(Sink.ignore)(Keep.left)
      .run()

    envelopes.foreach(e => actor ! e)

    switch
  }

  def receiveCbes: Collector[FlowEnvelope] = receiveMessages(
    headerCfg = ctxt.bs.headerConfig,
    cf = cf,
    dest = JmsQueue("cbeOut")
  )

  "The CBE Flow should" - {

    "Generate a CBE event (started) for a transaction started event" in {

      val started = FlowTransaction.startEvent()

      val switch = sendTransactions(ctxt = ctxt, cf = cf)(transactionEnvelope(ctxt, started))
      val cbeColl = receiveCbes

      val cbes = Await.result(cbeColl.result, timeout + 1.second)
      cbes should have size 1

      val t = FlowTransaction.envelope2Transaction(ctxt.bs.headerConfig)(cbes.head)
      t.state should be (started.state)
      t.tid should be (started.transactionId)

      switch.shutdown()
    }
  }
}
