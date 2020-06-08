package blended.jms.bridge.internal

import akka.NotUsed
import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import blended.container.context.api.ContainerContext
import blended.jms.bridge._
import blended.jms.bridge.internal.BridgeController.{AddConnectionFactory, RemoveConnectionFactory}
import blended.jms.utils.{IdAwareConnectionFactory, JmsDestination}
import blended.streams.message.FlowEnvelope
import blended.streams.{BlendedStreamsConfig, FlowHeaderConfig, StreamController}
import blended.util.config.Implicits._
import blended.util.logging.Logger
import com.typesafe.config.Config

import scala.jdk.CollectionConverters._
import scala.concurrent.duration._
import scala.util.{Failure, Success}

private[bridge] object BridgeControllerConfig {

  //noinspection NameBooleanParameters
  def create(
    cfg : Config,
    internalCf : IdAwareConnectionFactory,
    ctCtxt: ContainerContext,
    streamsCfg : BlendedStreamsConfig,
    streamBuilderFactory : ActorSystem => (BridgeStreamConfig, BlendedStreamsConfig) => BridgeStreamBuilder
  ) : BridgeControllerConfig = {

    val headerCfg = FlowHeaderConfig.create(ctCtxt)

    val providerList = cfg.getConfigList("provider").asScala.map { p =>
      BridgeProviderConfig.create(ctCtxt, p).get
    }.toList

    val inboundList : List[InboundConfig] =
      cfg.getConfigList("inbound", List.empty).map { i =>
        InboundConfig.create(ctCtxt, i).get
      }

    val trackInbound : Boolean = cfg.getBoolean("trackInbound", true)

    val alternates : Seq[String] = cfg.getStringListOption("outboundAlternateHeader").getOrElse(List.empty)

    providerList.filter(_.internal) match {
      case Nil      => throw new Exception("Exactly one provider must be marked as the internal provider for the JMS bridge.")
      case _ :: Nil =>
      case _        => throw new Exception("Exactly one provider must be marked as the internal provider for the JMS bridge.")
    }

    val registry = new BridgeProviderRegistry(providerList)

    BridgeControllerConfig(
      internalCf = internalCf,
      registry = registry,
      headerCfg = headerCfg,
      inbound = inboundList,
      outboundAlternates = alternates,
      ctCtxt = ctCtxt,
      rawConfig = cfg,
      streamsCfg = streamsCfg,
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
  outboundAlternates : Seq[String],
  ctCtxt : ContainerContext,
  trackInbound : Boolean,
  streamsCfg : BlendedStreamsConfig,
  rawConfig : Config,
  streamBuilderFactory : ActorSystem => (BridgeStreamConfig, BlendedStreamsConfig) => BridgeStreamBuilder
)

object BridgeController {

  case class AddConnectionFactory(cf : IdAwareConnectionFactory)
  case class RemoveConnectionFactory(cf : IdAwareConnectionFactory)

  def props(ctrlCfg : BridgeControllerConfig)(implicit system : ActorSystem) : Props =
    Props(new BridgeController(ctrlCfg))

}

class BridgeController(ctrlCfg : BridgeControllerConfig)(implicit system : ActorSystem) extends Actor {

  private[this] val log = Logger[BridgeController]

  // This is the map of active streams
  private[this] var streams : Map[String, ActorRef] = Map.empty

  private[this] def createInboundStream(in : InboundConfig, cf : IdAwareConnectionFactory, internal : Boolean) : Unit = {

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
      outboundAlternates = Seq.empty,
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
      ctCtxt = Some(ctrlCfg.ctCtxt),
      sessionRecreateTimeout = in.sessionRecreateTimeout
    )

    val builder = ctrlCfg.streamBuilderFactory(system)(inCfg, ctrlCfg.streamsCfg)
    val actor = context.actorOf(StreamController.props[FlowEnvelope, NotUsed](
      streamName = builder.streamId,
      src = builder.stream,
      streamCfg = ctrlCfg.streamsCfg
    )(onMaterialize = _ => ()))

    streams += (builder.streamId -> actor)
  }

  private[this] def createOutboundStream(cf : IdAwareConnectionFactory, internal : Boolean, alternates : Seq[String]) : Unit = {

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
      outboundAlternates = alternates,
      listener = 3,
      selector = None,
      registry = ctrlCfg.registry,
      trackTransaction = TrackTransaction.FromMessage,
      subscriberName = None,
      header = List.empty,
      sessionRecreateTimeout = 1.second
    )

    val builder = ctrlCfg.streamBuilderFactory(system)(outCfg, ctrlCfg.streamsCfg)
    val actor = context.actorOf(StreamController.props[FlowEnvelope, NotUsed](
      streamName = builder.streamId,
      src = builder.stream,
      streamCfg = ctrlCfg.streamsCfg
    )(onMaterialize = _ => ()))

    streams += (builder.streamId -> actor)
  }

  override def receive : Receive = {
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

          createOutboundStream(cf, internal, ctrlCfg.outboundAlternates)
        case Failure(_) =>
          log.warn("No internal JMS provider found in config")
      }

    case RemoveConnectionFactory(cf) =>
      log.info(s"Removing connection factory [${cf.vendor}:${cf.provider}]")

      streams.filter { case (key, _) => key.startsWith(cf.id) }.foreach {
        case (id, stream) =>
          log.info(s"Stopping stream [$id]")
          stream ! StreamController.Stop
          streams -= id
      }
  }
}
