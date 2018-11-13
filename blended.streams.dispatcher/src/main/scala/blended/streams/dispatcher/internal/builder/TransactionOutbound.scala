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
)(implicit system : ActorSystem, bs: DispatcherBuilderSupport) {

  private implicit val materializer : Materializer = ActorMaterializer()
  private val config = dispatcherCfg.providerRegistry.mandatoryProvider(internalCf.vendor, internalCf.provider)

  private [builder] val jmsSource : Try[Source[FlowEnvelope, NotUsed]] = Try {
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

  def build() : ActorRef = {

    val sendFlow = new CbeSendFlow(
      headerConfig = headerConfig,
      dispatcherCfg = dispatcherCfg,
      internalCf = internalCf,
      log = log
    ).build()

    val transactionStream : Flow[FlowEnvelope, FlowEnvelope, NotUsed] =
      new FlowTransactionStream(headerConfig, tMgr, log, sendFlow).build()

    val src : Source[FlowEnvelope, NotUsed] = jmsSource.get.via(transactionStream)

    val streamCfg = StreamControllerConfig(
      name = "transactionOut",
      source = src
    )

    system.actorOf(StreamController.props(streamCfg))
  }
}
