package blended.streams.dispatcher.internal.builder

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.scaladsl.{Broadcast, Flow, GraphDSL, Merge, Zip}
import akka.stream.{ActorMaterializer, FlowShape, Graph, Materializer}
import blended.jms.utils.IdAwareConnectionFactory
import blended.streams.FlowProcessor
import blended.streams.dispatcher.internal.ResourceTypeRouterConfig
import blended.streams.jms._
import blended.streams.message.FlowEnvelope
import blended.streams.transaction.{FlowHeaderConfig, FlowTransaction, FlowTransactionState}
import blended.util.logging.Logger

import scala.util.Try

class CbeSendFlow(
  headerConfig : FlowHeaderConfig,
  dispatcherCfg : ResourceTypeRouterConfig,
  internalCf: IdAwareConnectionFactory,
  log: Logger
)(implicit system : ActorSystem, bs: DispatcherBuilderSupport) extends JmsStreamSupport {

  private implicit val materializer : Materializer = ActorMaterializer()
  private val config = dispatcherCfg.providerRegistry.mandatoryProvider(internalCf.vendor, internalCf.provider)

  //TODO: Refactor
  private[builder] val jmsSink : Try[Flow[FlowEnvelope, FlowEnvelope, NotUsed]] = Try {

    val resolver : JmsProducerSettings => JmsDestinationResolver = settings => new DispatcherDestinationResolver(
      settings = settings,
      registry = dispatcherCfg.providerRegistry,
      bs = bs
    )

    val sinkSettings = JmsProducerSettings(
      headerConfig = headerConfig,
      connectionFactory = internalCf,
      jmsDestination = Some(config.get.cbes),
      deliveryMode = JmsDeliveryMode.Persistent,
      destinationResolver = resolver
    )

    val prepareCbe = Flow.fromFunction[FlowEnvelope, FlowEnvelope] { env =>

      val v = env.header[String](bs.headerEventVendor) match {
        case None => dispatcherCfg.eventProvider.vendor
        case Some(s) => s
      }

      val p = env.header[String](bs.headerEventProvider) match {
        case None => dispatcherCfg.eventProvider.provider
        case Some(s) => s
      }

      val provider = dispatcherCfg.providerRegistry.jmsProvider(v,p).get

      val result = env
        .withHeader(bs.headerBridgeVendor, v).get
        .withHeader(bs.headerBridgeProvider, p).get
        .withHeader(bs.headerBridgeDest, provider.cbes.asString).get
        .withHeader(bs.headerDeliveryMode, JmsDeliveryMode.Persistent.asString).get
        .withHeader(bs.headerConfig.headerTrack, false).get

      bs.streamLogger.debug(s"Prepared to send CBE [$result]")
      result
    }

    prepareCbe.via(
      jmsProducer(
        name = "cbeoutbound",
        settings = sinkSettings,
        log = log
      )
    )
  }

  private[builder] val logTransaction : Flow[FlowTransaction, FlowTransaction, NotUsed] =
    Flow.fromFunction[FlowTransaction, FlowTransaction] { t =>
      log.info(t.toString)
      t
    }

  private[builder] val toTrans : Flow[FlowEnvelope, FlowTransaction, NotUsed] = {
    Flow.fromFunction { env => FlowTransaction.envelope2Transaction(headerConfig)(env) }
  }

  private[builder] val toEnvelope : Flow[FlowTransaction, FlowEnvelope, NotUsed] =
    Flow.fromFunction { t => FlowTransaction.transaction2envelope(headerConfig)(t) }

  private[builder] val ackEnv = Flow.fromFunction[(FlowEnvelope, FlowEnvelope), FlowEnvelope]{ case (org, _) =>
    org.acknowledge()
    org
  }

  private[builder] val cbeFilter = FlowProcessor.partition[FlowEnvelope] { env =>
    env.header[Boolean](bs.headerCbeEnabled).getOrElse(true)
  }

  private[builder] val sendFlow : Try[Flow[FlowEnvelope, FlowEnvelope, NotUsed]] = Try {
    val g : Graph[FlowShape[FlowEnvelope, FlowEnvelope], NotUsed]= GraphDSL.create() { implicit b =>
      import GraphDSL.Implicits._

      val split = b.add(Broadcast[FlowEnvelope](2))

      val trans = b.add(toTrans)
      val logger = b.add(logTransaction)
      val stateFilter = b.add(Flow[FlowTransaction].filter(_.state != FlowTransactionState.Updated))
      val env = b.add(toEnvelope)
      val cbe = b.add(jmsSink.get)

      val cbeSplit = b.add(cbeFilter)

      val cbeMerge = b.add(Merge[FlowEnvelope](2))

      split.out(1) ~> trans ~> stateFilter ~> logger ~> env ~> cbeSplit.in

      cbeSplit.out0 ~> cbe ~> cbeMerge.in(0)
      cbeSplit.out1 ~> cbeMerge.in(1)

      val zip = b.add(Zip[FlowEnvelope, FlowEnvelope]())

      cbeMerge.out ~> zip.in1
      split.out(0) ~> zip.in0

      val ack = b.add(ackEnv)

      zip.out ~> ack.in

      FlowShape(split.in, ack.out)
    }

    Flow.fromGraph(g)
  }

  def build() : Flow[FlowEnvelope, FlowEnvelope, NotUsed] = sendFlow.get
}
