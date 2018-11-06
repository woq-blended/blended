package blended.streams.dispatcher.internal.builder

import akka.NotUsed
import akka.actor.{ActorRef, ActorSystem}
import akka.stream.scaladsl.{Broadcast, Flow, GraphDSL, Sink, Source}
import akka.stream.{ActorMaterializer, FlowShape, Graph, Materializer}
import blended.jms.bridge.BridgeProviderRegistry
import blended.jms.utils.IdAwareConnectionFactory
import blended.streams.jms._
import blended.streams.message.FlowEnvelope
import blended.streams.transaction._
import blended.streams.transaction.internal.FlowTransactionStream
import blended.streams.{StreamController, StreamControllerConfig}
import blended.util.logging.Logger

import scala.util.Try

class TransactionOutbound(
  headerConfig : FlowHeaderConfig,
  tMgr : ActorRef,
  registry: BridgeProviderRegistry,
  internalCf: IdAwareConnectionFactory,
  log: Logger
)(implicit system : ActorSystem, bs: DispatcherBuilderSupport) extends JmsStreamSupport {

  private implicit val materializer : Materializer = ActorMaterializer()
  private val config = registry.mandatoryProvider(internalCf.vendor, internalCf.provider)

  private val jmsSource : Try[Source[FlowEnvelope, NotUsed]] = Try {
    val srcSettings = JMSConsumerSettings(
      connectionFactory = internalCf,
      headerConfig = headerConfig,
    )
    .withSessionCount(3)
    .withDestination(Some(config.get.transactions))
    .withAcknowledgeMode(AcknowledgeMode.ClientAcknowledge)

    RestartableJmsSource(
      name = "transactionoutbound",
      settings = srcSettings,
      log = log
    )
  }

  private val jmsSink : Try[Sink[FlowEnvelope, NotUsed]] = Try {
    val sinkSettings = JmsProducerSettings(
      headerConfig = headerConfig,
      connectionFactory = internalCf,
      jmsDestination = Some(config.get.cbes),
      deliveryMode = JmsDeliveryMode.Persistent
    )

    jmsProducer(
      name = "cbeoutbound",
      settings = sinkSettings,
      log = log
    ).to(Sink.ignore)
  }

  private val logTransaction : Flow[FlowTransaction, FlowTransaction, NotUsed] =
    Flow.fromFunction[FlowTransaction, FlowTransaction] { t =>
      log.info(t.toString)
      t
    }

  private val toTrans : Flow[FlowEnvelope, FlowTransaction, NotUsed] = {
    Flow.fromFunction { env => FlowTransaction.envelope2Transaction(headerConfig)(env) }
  }

  private val toEnvelope : Flow[FlowTransaction, FlowEnvelope, NotUsed] =
    Flow.fromFunction { t => FlowTransaction.transaction2envelope(headerConfig)(t) }

  private val sendFlow : Try[Flow[FlowEnvelope, FlowEnvelope, NotUsed]] = Try {
    val g : Graph[FlowShape[FlowEnvelope, FlowEnvelope], NotUsed]= GraphDSL.create() { implicit b =>
      import GraphDSL.Implicits._

      val split = b.add(Broadcast[FlowEnvelope](2))

      val trans = b.add(toTrans)
      val logger = b.add(logTransaction)
      val filter = b.add(Flow[FlowTransaction].filter(_.state != FlowTransactionState.Updated))
      val env = b.add(toEnvelope)
      val cbe = b.add(jmsSink.get)
      val cbeFilter = b.add(Flow[FlowEnvelope].filter{ env =>
        env.header[Boolean](bs.headerCbeEnabled).getOrElse(true)
      })

      split.out(1) ~> trans ~> filter ~> logger ~> env ~> cbeFilter ~> cbe

      FlowShape(split.in, split.out(0))
    }

    Flow.fromGraph(g)
  }

  def build() : ActorRef = {

    val transactionStream : Flow[FlowEnvelope, FlowEnvelope, NotUsed] =
      new FlowTransactionStream(headerConfig, tMgr, log, sendFlow.get).build()

    val src : Source[FlowEnvelope, NotUsed] = jmsSource.get.via(transactionStream)

    val streamCfg = StreamControllerConfig(
      name = "transactionOut",
      source = src
    )

    system.actorOf(StreamController.props(streamCfg))
  }
}
