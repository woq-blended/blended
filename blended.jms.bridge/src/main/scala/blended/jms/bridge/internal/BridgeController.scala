package blended.jms.bridge.internal

import akka.NotUsed
import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import akka.stream.Materializer
import akka.stream.scaladsl.Source
import blended.container.context.api.ContainerIdentifierService
import blended.jms.bridge.internal.BridgeController.{AddConnectionFactory, RemoveConnectionFactory}
import blended.jms.bridge.{BridgeProviderConfig, BridgeProviderRegistry, JmsProducerSupport, RestartableJmsSource}
import blended.jms.utils.{IdAwareConnectionFactory, JmsDestination}
import blended.streams.jms._
import blended.streams.message.FlowEnvelope
import blended.streams.{StreamController, StreamControllerConfig}
import blended.util.logging.Logger
import com.typesafe.config.Config

import scala.collection.JavaConverters._
import scala.util.{Failure, Success}
import blended.util.config.Implicits._

private[bridge] object BridgeControllerConfig {

  def create(cfg : Config, internalCf : IdAwareConnectionFactory, idSvc: ContainerIdentifierService) : BridgeControllerConfig = {

    val headerPrefix = idSvc.containerContext.getContainerConfig().getString("blended.flow.headerPrefix", JmsSettings.defaultHeaderPrefix)

    val providerList = cfg.getConfigList("provider").asScala.map { p =>
      BridgeProviderConfig.create(idSvc, p).get
    }.toList

    val inboundList : List[InboundConfig ]=
      cfg.getConfigList("inbound", List.empty).map { i =>
        InboundConfig.create(idSvc, i).get
      }

    val queuePrefix = cfg.getString("queuePrefix", "blended.bridge")

    val (internalVendor, internalProvider) = providerList.filter(_.internal) match {
      case Nil => throw new Exception("Exactly one provider must be marked as the internal provider for the JMS bridge.")
      case h :: Nil => (h.vendor, h.provider)
      case h :: _ => throw new Exception("Exactly one provider must be marked as the internal provider for the JMS bridge.")
    }

    val registry = new BridgeProviderRegistry(providerList)

    BridgeControllerConfig(
      internalCf = internalCf,
      registry = registry,
      headerPrefix = headerPrefix,
      inbound = inboundList
    )
  }
}

private[bridge] case class BridgeControllerConfig(
  internalCf : IdAwareConnectionFactory,
  registry : BridgeProviderRegistry,
  headerPrefix : String,
  inbound : List[InboundConfig]
)

object BridgeController{

  private[this] val log = Logger[BridgeController]

  case class AddConnectionFactory(cf : IdAwareConnectionFactory)
  case class RemoveConnectionFactory(cf : IdAwareConnectionFactory)

  def props(ctrlCfg: BridgeControllerConfig)(implicit system : ActorSystem, materializer: Materializer) : Props =
    Props(new BridgeController(ctrlCfg))

  def bridgeStream(
    ctrlCfg : BridgeControllerConfig,
    fromCf : IdAwareConnectionFactory,
    fromDest : JmsDestination,
    toCf : IdAwareConnectionFactory,
    toDest : Option[JmsDestination],
    listener : Int,
    selector : Option[String] = None
  )(implicit system: ActorSystem, materializer: Materializer) : StreamControllerConfig = {

    val streamId = s"${fromCf.id}:${fromDest.asString}->${toCf.id}:${toDest.map(_.asString).getOrElse("out")}"

    val srcSettings = JMSConsumerSettings(fromCf)
      .withHeaderPrefix(ctrlCfg.headerPrefix)
      .withDestination(Some(fromDest))
      .withSessionCount(listener)
      .withSelector(selector)

    val destResolver = toDest match {
      case Some(d) => s : JmsProducerSettings => new SettingsDestinationResolver(s)
      case None => s : JmsProducerSettings => new MessageDestinationResolver(s)
    }

    val toSettings = JmsProducerSettings(toCf)
      .withDestination(toDest)
      .withDestinationResolver(destResolver)
      .withDeliveryMode(JmsDeliveryMode.Persistent)
      .withHeaderPrefix(ctrlCfg.headerPrefix)

    val bridgeLogger = Logger(streamId)

    // We will stream from the inbound destination to the inbound destination of the internal provider
    val stream : Source[FlowEnvelope, NotUsed] =
      RestartableJmsSource(name = streamId, settings = srcSettings, requiresAck = true, log = bridgeLogger)
        .via(JmsProducerSupport.jmsProducer(name = streamId, settings = toSettings, autoAck = true, log = Some(bridgeLogger)))

    // The stream will be handled by an actor which that can be used to shutdown the stream
    // and will restart the stream with a backoff strategy on failure
    StreamControllerConfig(
      name = streamId, source = stream
    )
  }
}

class BridgeController(ctrlCfg: BridgeControllerConfig)(implicit system : ActorSystem, materializer: Materializer) extends Actor{

  private[this] val log = Logger[BridgeController]

  // This is the map of active streams
  private[this] var streams : Map[String, ActorRef] = Map.empty

  // Register any required internal streams

  private[this] def createInternalStreams() : Unit = {
  }

  private[this] def createInboundStream(in : InboundConfig, cf : IdAwareConnectionFactory) : Unit = {

    val toDest = JmsDestination.create(
      ctrlCfg.registry.internalProvider.get.inbound.asString + "." + cf.vendor + "." + cf.provider
    ).get

    val streamCfg: StreamControllerConfig = BridgeController.bridgeStream(
      ctrlCfg = ctrlCfg,
      fromCf = cf,
      fromDest = in.from,
      toCf = ctrlCfg.internalCf,
      toDest = Some(toDest),
      listener = in.listener,
      selector = in.selector
    )

    streams += (streamCfg.name -> context.actorOf(StreamController.props(streamCfg)))
  }

  private[this] def createOutboundStream(in: InboundConfig, cf : IdAwareConnectionFactory) : Unit = {

    val fromDest = JmsDestination.create(
      ctrlCfg.registry.internalProvider.get.outbound.asString + "." + cf.vendor + "." + cf.provider
    ).get

    val streamCfg: StreamControllerConfig = BridgeController.bridgeStream(
      ctrlCfg = ctrlCfg,
      fromCf = ctrlCfg.internalCf,
      fromDest = fromDest,
      toCf = cf,
      toDest = None,
      listener = in.listener,
      selector = None
    )

    streams += (streamCfg.name -> context.actorOf(StreamController.props(streamCfg)))
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

            log.debug(s"Creating Streams for : [${inbound.mkString(",")}]")
            inbound.foreach { in =>
              createInboundStream(in, cf)
              createOutboundStream(in, cf)
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
