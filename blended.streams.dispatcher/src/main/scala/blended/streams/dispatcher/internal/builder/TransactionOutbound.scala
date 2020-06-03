package blended.streams.dispatcher.internal.builder

import akka.NotUsed
import akka.actor.{ActorRef, ActorSystem}
import akka.stream.scaladsl.{Flow, GraphDSL, Merge, Source}
import akka.stream.{FlowShape, Graph}
import blended.jms.utils.{IdAwareConnectionFactory, JmsDestination}
import blended.streams.dispatcher.internal.ResourceTypeRouterConfig
import blended.streams.jms._
import blended.streams.message.{FlowEnvelope, FlowEnvelopeLogger}
import blended.streams.processor.AckProcessor
import blended.streams.transaction._
import blended.streams._
import blended.util.logging.LogLevel

import scala.util.Try

class TransactionOutbound(
  headerConfig : FlowHeaderConfig,
  tMgr : FlowTransactionManager,
  dispatcherCfg : ResourceTypeRouterConfig,
  internalCf: IdAwareConnectionFactory,
  streamsCfg : BlendedStreamsConfig,
  log: FlowEnvelopeLogger
)(implicit system : ActorSystem, bs: DispatcherBuilderSupport) extends JmsStreamSupport {

  private val config = dispatcherCfg.providerRegistry.mandatoryProvider(internalCf.vendor, internalCf.provider)

  private[builder] val jmsSource : Try[Source[FlowEnvelope, NotUsed]] = Try {

    val transDest : JmsDestination = streamsCfg.transactionShard match {
      case None => config.get.transactions
      case Some(shard) =>
        val d = JmsDestination.asString(config.get.transactions)
        JmsDestination.create(d + "." + shard).get
    }

    val srcSettings = JmsConsumerSettings(
      log = log,
      logLevel = _ => LogLevel.Debug,
      headerCfg = headerConfig,
      connectionFactory = internalCf
    )
      .withSessionCount(3)
      .withDestination(Some(transDest))
      .withAcknowledgeMode(AcknowledgeMode.ClientAcknowledge)

    jmsConsumer(
      name = "transactionOutbound",
      settings = srcSettings,
      minMessageDelay = None
    )
  }

  private val requiresCbe : FlowEnvelope => Boolean = { env =>

    val result: Boolean = env.header[Boolean](bs.headerCbeEnabled).getOrElse(false) &&
      FlowTransactionState.apply(
        env.header[String](bs.headerConfig.headerState).getOrElse(FlowTransactionStateUpdated.toString())
      ).get != FlowTransactionStateUpdated

    log.logEnv(env, LogLevel.Debug, s"CBE generation for envelope [${env.id}] is [$result]")
    result
  }

  private val sendCbe : Flow[FlowEnvelope, FlowEnvelope, NotUsed] = {

    val g : Graph[FlowShape[FlowEnvelope, FlowEnvelope], NotUsed] = GraphDSL.create(){ implicit b =>
      import GraphDSL.Implicits._

      val cbeSplit = b.add(FlowProcessor.partition[FlowEnvelope](requiresCbe))
      val join = b.add(Merge[FlowEnvelope](2))
      val cbeFlow = b.add(new CbeSendFlow(
        headerConfig = headerConfig,
        dispatcherCfg = dispatcherCfg,
        internalCf = internalCf,
        streamLogger = log
      ).build())

      cbeSplit.out0 ~> cbeFlow ~> join.in(0)
      cbeSplit.out1 ~> join.in(1)

      FlowShape(cbeSplit.in, join.out)
    }

    Flow.fromGraph(g)
  }

  def build() : ActorRef = {

    val transactionStream : Flow[FlowEnvelope, FlowEnvelope, NotUsed] =
      new FlowTransactionStream(
        headerCfg = headerConfig,
        internalCf = Some(internalCf),
        tMgr = tMgr,
        streamLogger = log
      ).build()


    val src : Source[FlowEnvelope, NotUsed] = jmsSource.get
      .via(transactionStream)
      .via(sendCbe)
      .via(new AckProcessor("transactionOutbound").flow)

    system.actorOf(StreamController.props[FlowEnvelope, NotUsed](
      streamName = "transactionOut",
      src = src,
      streamCfg = streamsCfg
    )(onMaterialize = _ => ()))
  }
}
