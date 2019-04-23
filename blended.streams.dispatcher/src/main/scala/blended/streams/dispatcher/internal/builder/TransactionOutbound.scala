package blended.streams.dispatcher.internal.builder

import akka.NotUsed
import akka.actor.{ActorRef, ActorSystem}
import akka.stream.scaladsl.{Flow, Source}
import akka.stream.{ActorMaterializer, Materializer}
import blended.jms.utils.IdAwareConnectionFactory
import blended.streams.dispatcher.internal.ResourceTypeRouterConfig
import blended.streams.jms._
import blended.streams.message.FlowEnvelope
import blended.streams.transaction.{FlowTransactionStream, _}
import blended.streams.{StreamController, StreamControllerConfig}
import blended.util.logging.Logger

import scala.util.Try

class TransactionOutbound(
  headerConfig : FlowHeaderConfig,
  tMgr : ActorRef,
  dispatcherCfg : ResourceTypeRouterConfig,
  internalCf: IdAwareConnectionFactory,
  log: Logger
)(implicit system : ActorSystem, bs: DispatcherBuilderSupport) extends JmsStreamSupport {

  private implicit val materializer : Materializer = ActorMaterializer()
  private val config = dispatcherCfg.providerRegistry.mandatoryProvider(internalCf.vendor, internalCf.provider)

  private [builder] val jmsSource : Try[Source[FlowEnvelope, NotUsed]] = Try {
    val srcSettings = JMSConsumerSettings(
      log = log,
      connectionFactory = internalCf,
    )
      .withSessionCount(3)
      .withDestination(Some(config.get.transactions))
      .withAcknowledgeMode(AcknowledgeMode.ClientAcknowledge)

    jmsConsumer(
      name = "transactionOutbound",
      settings = srcSettings,
      headerConfig = headerConfig,
      minMessageDelay = None
    )
  }

  def build() : ActorRef = {

    val sendFlow = new CbeSendFlow(
      headerConfig = headerConfig,
      dispatcherCfg = dispatcherCfg,
      internalCf = internalCf,
      log = log
    ).build()

    val transactionStream : Flow[FlowEnvelope, FlowEnvelope, NotUsed] =
      new FlowTransactionStream(
        cfg = headerConfig,
        tMgr = tMgr,
        log = log,
        // The default for CBE is false here
        // all messages that have run through the dispatcher will have the correct CBE setting
        performSend = { env =>
          env.header[Boolean](bs.headerCbeEnabled).getOrElse(false) &&
          FlowTransactionState.withName(
            env.header[String](bs.headerConfig.headerState).getOrElse(FlowTransactionState.Updated.toString())
          ) != FlowTransactionState.Updated
        },
        sendFlow
      ).build()


    val src : Source[FlowEnvelope, NotUsed] = jmsSource.get.via(transactionStream)

    val streamCfg = StreamControllerConfig.fromConfig(dispatcherCfg.rawConfig).get
      .copy(
        name = "transactionOut",
        source = src
      )

    system.actorOf(StreamController.props(streamCfg))
  }
}
