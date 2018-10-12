package blended.jms.bridge.internal

import akka.NotUsed
import akka.actor.{Actor, ActorRef, Props}
import akka.stream.scaladsl.Source
import blended.jms.bridge.internal.BridgeController.{AddConnectionFactory, RemoveConnectionFactory}
import blended.jms.utils.IdAwareConnectionFactory
import blended.streams.{FlowProcessor, StreamController, StreamControllerConfig}
import blended.streams.jms._
import blended.streams.message.FlowEnvelope
import blended.util.logging.Logger

private[bridge] case class BridgeControllerConfig(
  internalVendor : String,
  internalProvider : Option[String],
  internalConnectionFactory : IdAwareConnectionFactory,
  queuePrefix : String,
  headerPrefix : String,
  jmsProvider : List[BridgeProviderConfig],
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

      if (cf.id == ctrlCfg.internalConnectionFactory.id) {
        log.debug("Adding internal streams")
        createInternalStreams()
      } else {
        // Create inbound streams for all matching inbound configs
        val inProvider = ProviderFilter.listProviderFilter(ctrlCfg.inbound, cf.vendor, Some(cf.provider))
        log.debug(s"Creating inbound Streams : [${inProvider.mkString(",")}]")

        inProvider.foreach { in =>
          val streamId = s"${cf.id}:${in.name}"
          log.info(s"Creating inbound Stream [$streamId]")

          val srcSettings = JMSConsumerSettings(cf)
            .withHeaderPrefix(ctrlCfg.headerPrefix)
            .withDestination(Some(in.from))
            .withSessionCount(in.listener)
            .withSelector(in.selector)

          val toSettings = JmsProducerSettings(ctrlCfg.internalConnectionFactory)
            .withDestination(Some(in.to))
            .withDeliveryMode(JmsDeliveryMode.Persistent)
            .withHeaderPrefix(ctrlCfg.headerPrefix)

          val source :
            Source[FlowEnvelope, NotUsed] =
            Source.fromGraph(new JmsAckSourceStage(srcSettings, context.system))
              .via(FlowProcessor.log(s"$streamId-log")(Logger(s"bridge.in.${in.from.asString}")))
              .via(new JmsSinkStage(toSettings)(context.system))
              .via(FlowProcessor.ack(s"$streamId-ack")(log))
              .via(FlowProcessor.log(s"$streamId-log")(Logger(s"bridge.out.${in.to.asString}")))

          val ctrlConfig = StreamControllerConfig(
            name = streamId, stream = source
          )

          streams += (streamId -> context.actorOf(StreamController.props(ctrlConfig)))
        }
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
