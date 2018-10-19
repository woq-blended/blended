package blended.jms.bridge.internal

import akka.NotUsed
import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import akka.stream.Materializer
import akka.stream.scaladsl.Source
import blended.jms.bridge.{BridgeProviderRegistry, JmsProducerSupport, RestartableJmsSource}
import blended.jms.bridge.internal.BridgeController.{AddConnectionFactory, RemoveConnectionFactory}
import blended.jms.utils.{IdAwareConnectionFactory, JmsDestination}
import blended.streams.jms._
import blended.streams.message.FlowEnvelope
import blended.streams.processor.AckProcessor
import blended.streams.{StreamController, StreamControllerConfig}
import blended.util.logging.{LogLevel, Logger}

import scala.util.{Failure, Success}

private[bridge] case class BridgeControllerConfig(
  internalCf : IdAwareConnectionFactory,
  registry : BridgeProviderRegistry,
  queuePrefix : String,
  headerPrefix : String,
  inbound : List[InboundConfig]
)

object BridgeController{

  case class AddConnectionFactory(cf : IdAwareConnectionFactory)
  case class RemoveConnectionFactory(cf : IdAwareConnectionFactory)

  def props(ctrlCfg: BridgeControllerConfig)(implicit system : ActorSystem, materializer: Materializer) : Props =
    Props(new BridgeController(ctrlCfg))
}

class BridgeController(ctrlCfg: BridgeControllerConfig)(implicit system : ActorSystem, materializer: Materializer) extends Actor{

  private[this] val log = Logger[BridgeController]

  // This is the map of active streams
  private[this] var streams : Map[String, ActorRef] = Map.empty

  // Register any required internal streams

  private[this] def createInternalStreams() : Unit = {
  }

  private[this] def createInboundStream(in : InboundConfig, cf : IdAwareConnectionFactory) : Unit = {
    val streamId = s"${cf.id}:${in.name}"

    // TODO: Handle exception (should not occurr)
    val toDest = JmsDestination.create(
      ctrlCfg.registry.internalProvider.get.inbound.asString + "." + cf.vendor + "." + cf.provider
    ).get

    log.info(s"Creating inbound Stream [$streamId] with [${in.listener}] listeners from [${in.from}] to [${toDest}]")

    val srcSettings = JMSConsumerSettings(cf)
      .withHeaderPrefix(ctrlCfg.headerPrefix)
      .withDestination(Some(in.from))
      .withSessionCount(in.listener)
      .withSelector(in.selector)

    val toSettings = JmsProducerSettings(ctrlCfg.internalCf)
      .withDestination(Some(toDest))
      .withDeliveryMode(JmsDeliveryMode.Persistent)
      .withHeaderPrefix(ctrlCfg.headerPrefix)

    val name = s"flow.bridge.${in.name}"
    val bridgeLogger = Logger(name)

    // Todo: create outbound bridge, abstract logger
    // We will stream from the inbound destination to the inbound destination of the internal provider
    val stream : Source[FlowEnvelope, NotUsed] =
    RestartableJmsSource(name = name, settings = srcSettings, requiresAck = true, log = bridgeLogger)
      .via(JmsProducerSupport.jmsProducer(name = name, settings = toSettings, autoAck = true, log = Some(bridgeLogger)))

    // The stream will be handled by an actor which that can be used to shutdown the stream
    // and will restart the stream with a backoff strategy on failure
    val ctrlConfig = StreamControllerConfig(
      name = streamId, source = stream
    )

    streams += (streamId -> context.actorOf(StreamController.props(ctrlConfig)))
  }

  override def receive: Receive = {
    case AddConnectionFactory(cf) =>
      log.info(s"Adding connection factory [${cf.id}]")

      ctrlCfg.registry.internalProvider match {
        case Success(p) =>
          if (cf.id == p.id) {
            log.debug("Adding internal streams")
            createInternalStreams()
          } else {
            // TODO: Crosscheck provider registry

            // Create inbound streams for all matching inbound configs
            val inbound : List[InboundConfig] = ctrlCfg.inbound.filter { in =>
              ProviderFilter(in.vendor, in.provider).matches(cf)
            }

            log.debug(s"Creating inbound Streams : [${inbound.mkString(",")}]")

            inbound.foreach { in =>
              createInboundStream(in, cf)
            }
          }

        case Failure(t) =>
          log.warn("No internal JMS provider found in config")
      }

    case RemoveConnectionFactory(cf) => {
      log.info(s"Removing connection factory [${cf.vendor}:${cf.provider}]")

      streams.filter{ case (key, stream) => key.startsWith(cf.id) }.foreach { case (id, stream) =>

        log.info(s"Stopping stream [$id]")
        stream ! StreamController.Stop
        streams -= id
      }
    }
  }
}
