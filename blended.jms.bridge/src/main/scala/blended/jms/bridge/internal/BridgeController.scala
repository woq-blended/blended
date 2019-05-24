package blended.jms.bridge.internal

import akka.NotUsed
import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import akka.stream.Materializer
import blended.container.context.api.ContainerIdentifierService
import blended.jms.bridge._
import blended.jms.bridge.internal.BridgeController.{AddConnectionFactory, RemoveConnectionFactory}
import blended.jms.utils.{IdAwareConnectionFactory, JmsDestination}
import blended.streams.message.FlowEnvelope
import blended.streams.transaction.FlowHeaderConfig
import blended.streams.StreamController
import blended.util.config.Implicits._
import blended.util.logging.Logger
import com.typesafe.config.Config

import scala.collection.JavaConverters._
import scala.concurrent.duration._
import scala.util.{Failure, Success}

private[bridge] object BridgeControllerConfig {

  def create(
    cfg : Config,
    internalCf : IdAwareConnectionFactory,
    idSvc: ContainerIdentifierService,
    streamBuilderFactory : ActorSystem => Materializer => BridgeStreamConfig => BridgeStreamBuilder
  ) : BridgeControllerConfig = {

    val headerCfg = FlowHeaderConfig.create(idSvc)

    val providerList = cfg.getConfigList("provider").asScala.map { p =>
      BridgeProviderConfig.create(idSvc, p).get
    }.toList

    val inboundList : List[InboundConfig ]=
      cfg.getConfigList("inbound", List.empty).map { i =>
        InboundConfig.create(idSvc, i).get
      }

    val trackInbound : Boolean = cfg.getBoolean("trackInbound", true)

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
      inbound = inboundList,
      idSvc = idSvc,
      rawConfig = cfg,
      trackInbound = trackInbound,
      streamBuilderFactory = streamBuilderFactory
    )
  }
}

private[bridge] case class BridgeControllerConfig(
  internalCf : IdAwareConnectionFactory,
  registry : BridgeProviderRegistry,
  headerCfg : FlowHeaderConfig,
  inbound : List[InboundConfig],
  idSvc : ContainerIdentifierService,
  trackInbound : Boolean,
  rawConfig : Config,
  streamBuilderFactory : ActorSystem => Materializer => BridgeStreamConfig => BridgeStreamBuilder
)

object BridgeController{

  case class AddConnectionFactory(cf : IdAwareConnectionFactory)
  case class RemoveConnectionFactory(cf : IdAwareConnectionFactory)

  def props(ctrlCfg: BridgeControllerConfig)(implicit system : ActorSystem, materializer: Materializer) : Props =
    Props(new BridgeController(ctrlCfg))

}

class BridgeController(ctrlCfg: BridgeControllerConfig)(implicit system : ActorSystem, materializer: Materializer) extends Actor {

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

    val inCfg = BridgeStreamConfig(
      inbound = true,
      fromCf = cf,
      fromDest = in.from,
      toCf = ctrlCfg.internalCf,
      toDest = Some(toDest),
      listener = in.listener,
      selector = in.selector,
      registry = ctrlCfg.registry,
      headerCfg = ctrlCfg.headerCfg,
      trackTransaction = if (ctrlCfg.trackInbound) {
        TrackTransaction.On
      } else {
        TrackTransaction.Off
      },
      subscriberName = in.subscriberName,
      header = in.header,
      idSvc = Some(ctrlCfg.idSvc),
      rawConfig = ctrlCfg.rawConfig,
      sessionRecreateTimeout = in.sessionRecreateTimeout
    )

    val builder = ctrlCfg.streamBuilderFactory(system)(materializer)(inCfg)
    val actor = context.actorOf(StreamController.props[FlowEnvelope, NotUsed](builder.stream, builder.streamCfg))

    streams += (builder.streamCfg.name -> actor)
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
    val outCfg = BridgeStreamConfig(
      inbound = false,
      headerCfg = ctrlCfg.headerCfg,
      fromCf = ctrlCfg.internalCf,
      fromDest = fromDest,
      toCf = cf,
      toDest = None,
      listener = 3,
      selector = None,
      registry = ctrlCfg.registry,
      trackTransaction = TrackTransaction.FromMessage,
      subscriberName = None,
      header = List.empty,
      rawConfig = ctrlCfg.rawConfig,
      sessionRecreateTimeout = 1.second
    )

    val builder = ctrlCfg.streamBuilderFactory(system)(materializer)(outCfg)
    val actor = context.actorOf(StreamController.props[FlowEnvelope, NotUsed](builder.stream, builder.streamCfg))

    streams += (builder.streamCfg.name -> actor)
  }

  override def receive: Receive = {
    case AddConnectionFactory(cf) =>

      ctrlCfg.registry.internalProvider match {
        case Success(p) =>
          val internal = p.vendor == cf.vendor && p.provider == cf.provider
          log.info(s"Adding connection factory [${cf.id}], internal [$internal]")

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

    case RemoveConnectionFactory(cf) =>
      log.info(s"Removing connection factory [${cf.vendor}:${cf.provider}]")

      streams.filter{ case (key, _) => key.startsWith(cf.id) }.foreach { case (id, stream) =>
        log.info(s"Stopping stream [$id]")
        stream ! StreamController.Stop
        streams -= id
      }
    }
}
