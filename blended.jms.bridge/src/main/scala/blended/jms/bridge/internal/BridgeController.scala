package blended.jms.bridge.internal

import akka.NotUsed
import akka.actor.{Actor, ActorRef, Props}
import akka.stream.scaladsl.Source
import blended.jms.bridge.{BridgeProviderConfig, BridgeProviderRegistry}
import blended.jms.bridge.internal.BridgeController.{AddConnectionFactory, RemoveConnectionFactory}
import blended.jms.utils.IdAwareConnectionFactory
import blended.streams.{FlowProcessor, StreamController, StreamControllerConfig}
import blended.streams.jms._
import blended.streams.message.FlowEnvelope
import blended.util.logging.Logger

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

  def props(ctrlCfg: BridgeControllerConfig) : Props =
    Props(new BridgeController(ctrlCfg))
}

class BridgeController(ctrlCfg: BridgeControllerConfig) extends Actor{

  private[this] val log = Logger[BridgeController]

  // This is the map of active streams
  private[this] var streams : Map[String, ActorRef] = Map.empty

  // Register any required internal streams

  private[this] def createInternalStreams() : Unit = {
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
            val inProvider : List[InboundConfig] = ctrlCfg.inbound.filter { in =>
              ProviderFilter(in.vendor, in.provider).matches(cf)
            }

            log.debug(s"Creating inbound Streams : [${inProvider.mkString(",")}]")

            inProvider.foreach { in =>
              val streamId = s"${cf.id}:${in.name}"
              log.info(s"Creating inbound Stream [$streamId]")

              val srcSettings = JMSConsumerSettings(cf)
                .withHeaderPrefix(ctrlCfg.headerPrefix)
                .withDestination(Some(in.from))
                .withSessionCount(in.listener)
                .withSelector(in.selector)

              // TODO: Handle exception (should not occurr)
              val toDest = ctrlCfg.registry.internalProvider.get.inbound

              val toSettings = JmsProducerSettings(ctrlCfg.internalCf)
                .withDestination(Some(toDest))
                .withDeliveryMode(JmsDeliveryMode.Persistent)
                .withHeaderPrefix(ctrlCfg.headerPrefix)

              val source :
                Source[FlowEnvelope, NotUsed] =
                Source.fromGraph(new JmsAckSourceStage(srcSettings, context.system))
                  .via(FlowProcessor.log(s"$streamId-log")(Logger(s"bridge.in.${in.from.asString}")))
                  .via(new JmsSinkStage(toSettings)(context.system))
                  .via(FlowProcessor.ack(s"$streamId-ack")(log))
                  .via(FlowProcessor.log(s"$streamId-log")(Logger(s"bridge.out.${toDest.asString}")))

              val ctrlConfig = StreamControllerConfig(
                name = streamId, stream = source
              )

              streams += (streamId -> context.actorOf(StreamController.props(ctrlConfig)))
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
