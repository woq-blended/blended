package blended.jms.bridge.internal

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import akka.stream.Materializer
import blended.container.context.api.ContainerIdentifierService
import blended.jms.bridge._
import blended.jms.bridge.internal.BridgeController.{AddConnectionFactory, RemoveConnectionFactory}
import blended.jms.utils.{IdAwareConnectionFactory, JmsDestination}
import blended.streams.transaction.FlowHeaderConfig
import blended.streams.{StreamController, StreamControllerConfig}
import blended.util.config.Implicits._
import blended.util.logging.Logger
import com.typesafe.config.Config

import scala.collection.JavaConverters._
import scala.util.{Failure, Success}

private[bridge] object BridgeControllerConfig {

  def create(
    cfg : Config, internalCf : IdAwareConnectionFactory, idSvc: ContainerIdentifierService
  ) : BridgeControllerConfig = {

    val headerCfg = FlowHeaderConfig.create(
       idSvc.containerContext.getContainerConfig().getConfig("blended.flow.header")
    )

    val providerList = cfg.getConfigList("provider").asScala.map { p =>
      BridgeProviderConfig.create(idSvc, p).get
    }.toList

    val inboundList : List[InboundConfig ]=
      cfg.getConfigList("inbound", List.empty).map { i =>
        InboundConfig.create(idSvc, i).get
      }

    providerList.filter(_.internal) match {
      case Nil => throw new Exception("Exactly one provider must be marked as the internal provider for the JMS bridge.")
      case _ :: Nil =>
      case _ => throw new Exception("Exactly one provider must be marked as the internal provider for the JMS bridge.")
    }

    val registry = new BridgeProviderRegistry(providerList)

    BridgeControllerConfig(
      internalCf = internalCf,
      registry = registry,
      headerCfg = headerCfg,
      inbound = inboundList
    )
  }
}

private[bridge] case class BridgeControllerConfig(
  internalCf : IdAwareConnectionFactory,
  registry : BridgeProviderRegistry,
  headerCfg : FlowHeaderConfig,
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

  private[this] def createInboundStream(in : InboundConfig, cf : IdAwareConnectionFactory, internal: Boolean) : Unit = {

    val toDest = if (internal) {
      JmsDestination.create(ctrlCfg.registry.internalProvider.get.inbound.asString).get
    } else {
      JmsDestination.create(
        ctrlCfg.registry.internalProvider.get.inbound.asString + "." + cf.vendor + "." + cf.provider
      ).get
    }

    val inCfg = JmsStreamConfig(
      inbound = true,
      fromCf = cf,
      fromDest = in.from,
      toCf = ctrlCfg.internalCf,
      toDest = Some(toDest),
      listener = in.listener,
      selector = in.selector,
      registry = ctrlCfg.registry,
      headerCfg = ctrlCfg.headerCfg,
      trackTransAction = TrackTransaction.On,
      subscriberName = in.subscriberName,
      header = in.header
    )

    val streamCfg: StreamControllerConfig = new JmsStreamBuilder(inCfg).streamCfg

    streams += (streamCfg.name -> context.actorOf(StreamController.props(streamCfg)))
  }

  private[this] def createOutboundStream(cf : IdAwareConnectionFactory, internal : Boolean) : Unit = {

    val fromDest = if (internal) {
      JmsDestination.create(ctrlCfg.registry.internalProvider.get.outbound.asString).get
    } else {
      JmsDestination.create(
        ctrlCfg.registry.internalProvider.get.outbound.asString + "." + cf.vendor + "." + cf.provider
      ).get
    }

    // TODO: Make listener count configurable
    val outCfg = JmsStreamConfig(
      inbound = false,
      headerCfg = ctrlCfg.headerCfg,
      fromCf = ctrlCfg.internalCf,
      fromDest = fromDest,
      toCf = cf,
      toDest = None,
      listener = 3,
      selector = None,
      registry = ctrlCfg.registry,
      trackTransAction = TrackTransaction.FromMessage,
      subscriberName = None,
      header = List.empty
    )

    val streamCfg: StreamControllerConfig = new JmsStreamBuilder(outCfg).streamCfg

    streams += (streamCfg.name -> context.actorOf(StreamController.props(streamCfg)))
  }

  override def receive: Receive = {
    case AddConnectionFactory(cf) =>
      log.info(s"Adding connection factory [${cf.id}]")

      ctrlCfg.registry.internalProvider match {
        case Success(p) =>
          val internal = p.vendor == cf.vendor && p.provider == cf.provider

          // Create inbound streams for all matching inbound configs
          val inbound : List[InboundConfig] = ctrlCfg.inbound.filter { in =>
            ProviderFilter(in.vendor, in.provider).matches(cf)
          }

          log.debug(s"Creating Streams for inbound destinations : [${inbound.mkString(",")}]")
          inbound.foreach { in => createInboundStream(in, cf, internal) }

          createOutboundStream(cf, internal)
        case Failure(_) =>
          log.warn("No internal JMS provider found in config")
      }

    case RemoveConnectionFactory(cf) => {
      log.info(s"Removing connection factory [${cf.vendor}:${cf.provider}]")

      streams.filter{ case (key, _) => key.startsWith(cf.id) }.foreach { case (id, stream) =>
        log.info(s"Stopping stream [$id]")
        stream ! StreamController.Stop
        streams -= id
      }
    }
  }
}
