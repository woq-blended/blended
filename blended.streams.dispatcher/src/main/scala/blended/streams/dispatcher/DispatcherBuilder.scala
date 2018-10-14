package blended.streams.dispatcher

import akka.NotUsed
import akka.stream._
import akka.stream.javadsl.RunnableGraph
import akka.stream.scaladsl.GraphDSL.Implicits._
import akka.stream.scaladsl.{Broadcast, Flow, GraphDSL, Sink, Source}
import blended.container.context.api.ContainerIdentifierService
import blended.streams.FlowProcessor
import blended.streams.dispatcher.internal.{OutboundRouteConfig, ResourceTypeConfig, ResourceTypeRouterConfig}
import blended.streams.message.{FlowEnvelope, FlowMessage}
import blended.streams.processor.{HeaderTransformProcessor, LogProcessor}
import blended.util.logging.{LogLevel, Logger}

import scala.reflect.ClassTag
import scala.util.{Success, Try}

class MissingResourceType(msg: FlowMessage)
  extends Exception(s"Missing ResourceType in [$msg] ")

class IllegalResourceType(msg : FlowMessage, rt: String)
  extends Exception(s"Illegal ResourceType [$rt] in [$msg]")

class MissingOutboundRouting(rt: String)
  extends Exception(s"At least one Outbound route must be configured for ResourceType [$rt]")

class MissingContextObject(key: String, clazz: String)
  extends Exception(s"Missing context object [$key], expected type [$clazz]")

object DispatcherBuilder {

  val resourceTypeHeader = "ResourceType"
  val rtConfigKey = classOf[ResourceTypeConfig].getSimpleName()
  val outboundCfgKey = classOf[OutboundRouteConfig].getSimpleName()

  // TODO: Move this to the API
  val HEADER_BRIDGE_VENDOR       = "SIBBridgeVendor"
  val HEADER_BRIDGE_PROVIDER     = "SIBBridgeProvider"
  val HEADER_BRIDGE_DEST         = "SIBBridgeDestination"

  val HEADER_CBE_ENABLED         = "SIBCbeEnabled"

  val HEADER_EVENT_VENDOR        = "SIBEventVendor"
  val HEADER_EVENT_PROVIDER      = "SIBEventProvider"
  val HEADER_EVENT_DEST          = "SIBEventDestination"  
}

case class DispatcherBuilder(

  dispatcherId : String,

  idSvc : ContainerIdentifierService,

  // The Dispatcher configuration
  cfg: ResourceTypeRouterConfig,

  // Inbound messages
  source : Source[FlowEnvelope, _],

  // Messages with normal outcome to be disptached via jms
  jmsOut : Sink[FlowEnvelope, _],

  // Events to be dispatched
  eventOut : Sink[FlowEnvelope, _],

  // Any error go here
  errorOut : Sink[FlowEnvelope, _]
) {

  import DispatcherBuilder._

  private[this] val streamLogger = Logger(s"dispatcher.$dispatcherId")
  private[this] val logger = Logger[DispatcherBuilder]

  /* The inbound flow simply consumes FlowEnvelopes from the source, adds all configured default headers
   * and logs the inbound message.
   *
   * Finally it checks, whether the Resourcetype passed in with the message is configured in the config.
  */
  private lazy val inbound : Graph[FlowShape[FlowEnvelope, FlowEnvelope], NotUsed] = {

    Flow.fromGraph(defaultHeader)
      .via(Flow.fromGraph(logStart))
      .via(Flow.fromGraph(checkResourceType))
  }

  /*-------------------------------------------------------------------------------------------------*/
  private lazy val defaultHeader = HeaderTransformProcessor(
    name = "defaultHeader",
    log = streamLogger,
    rules = cfg.defaultHeader.map(h => (h.name, h.value, h.overwrite)),
    idSvc = Some(idSvc)
  ).flow(logger)

  /*-------------------------------------------------------------------------------------------------*/
  private lazy val logStart = LogProcessor("logInbound", streamLogger, LogLevel.Info).flow(logger)

  /*-------------------------------------------------------------------------------------------------*/
  private lazy val checkResourceType = FlowProcessor.fromFunction("checkResourceType", streamLogger) { env =>
    env.flowMessage.header[String](resourceTypeHeader) match {
      case None =>
        val e = new MissingResourceType(env.flowMessage)
        streamLogger.error(e)(e.getMessage)
        Success(Seq(env.withException(e)))
      case Some(rt) =>
        cfg.resourceTypeConfigs.get(rt) match {
          case None =>
            val e = new IllegalResourceType(env.flowMessage, rt)
            streamLogger.error(e)(e.getMessage)
            Success(Seq(env.withException(e)))
          case Some(rtCfg) =>
            rtCfg.outbound match {
              case Nil =>
                val e = new MissingOutboundRouting(rt)
                streamLogger.error(e)(e.getMessage)
                Success(Seq(env.withException(e)))
              case _ =>
                Success(Seq(env.setInContext(rtConfigKey, rtCfg)))
            }
        }
    }
  }

  private def withContextObject[T](key : String, env: FlowEnvelope)(f : T => Seq[FlowEnvelope])(implicit classTag: ClassTag[T]) : Seq[FlowEnvelope] = {

    env.getFromContext[T](key).get match {

      case None => // Should not be possible
        val rt = env.flowMessage.header[String](resourceTypeHeader).getOrElse("UNKNOWN")
        val e = new MissingContextObject(key, classTag.runtimeClass.getName())
        streamLogger.error(e)(e.getMessage)
        Seq(env.withException(e))

      case Some(o) =>
        f(o)
    }
  }

  /*-------------------------------------------------------------------------------------------------*/
  private lazy val fanoutOutbound = FlowProcessor.fromFunction("fanoutOutbound", streamLogger) { env =>

    Try {
      withContextObject[ResourceTypeConfig](rtConfigKey, env) { rtCfg: ResourceTypeConfig =>
        val fanouts = rtCfg.outbound.map { ob => env.setInContext(outboundCfgKey, ob) }
        fanouts
      }
    }
  }

  /*-------------------------------------------------------------------------------------------------*/
  private lazy val decideCbe = FlowProcessor.fromFunction("decideCbe", streamLogger) { env =>

    Try {
      withContextObject[ResourceTypeConfig](rtConfigKey, env){ rtCfg : ResourceTypeConfig =>
        withContextObject[OutboundRouteConfig](outboundCfgKey, env) { cfg: OutboundRouteConfig =>

          if (rtCfg.withCBE) {
            val newMsg = env.flowMessage
              .withHeader(HEADER_EVENT_VENDOR, cfg.eventProvider.vendor).get
              .withHeader(HEADER_EVENT_PROVIDER, cfg.eventProvider.provider).get
              .withHeader(HEADER_EVENT_DEST, cfg.eventProvider.eventDestination.asString).get
              .withHeader(HEADER_CBE_ENABLED, true).get

            Seq(env.copy(flowMessage = newMsg))
          } else {
            Seq(env.withHeader(HEADER_CBE_ENABLED, false).get)
          }
        }
      }
    }
  }

  /*-------------------------------------------------------------------------------------------------*/
  def build(): RunnableGraph[NotUsed] = {

    val g  = RunnableGraph.fromGraph(GraphDSL.create() { implicit builder =>

      // These are the in- and outbounds of the dispatcher flow
      val in : Outlet[FlowEnvelope] = builder.add(source).out
      val jms : Inlet[FlowEnvelope] = builder.add(jmsOut).in
      val event : Inlet[FlowEnvelope] = builder.add(eventOut).in
      val error : Inlet[FlowEnvelope] = builder.add(errorOut).in

      // This is where we pick up the messages from the source, populate the headers
      // and perform initial checks if the message can be processed
      val header = builder.add(inbound)

      // The fanout step will produce one envelope per outbound config of the resource
      // sent in the message. The envelope context will contain the config for the outbound
      // branch and the overall resource type config.
      val fanout = builder.add(fanoutOutbound)

      // Finally, we define a subflow which processes each fanout messsage in turn,
      // it will generate CBE events, populate the outbound header sections and
      // finally set the external destination
      val processOutbound = builder.add(decideCbe)

      // The error splitter pushes all envelopes that have an exception defined to the error sink
      // and all messages without an exception defined to the normal sink
      val errorSplit = builder.add(Broadcast[FlowEnvelope](2))
      val toJms = builder.add(Flow[FlowEnvelope].filter(_.exception.isEmpty))
      val toError = builder.add(Flow[FlowEnvelope].filter(_.exception.isDefined))

      // wire up the steps
      in ~> header ~> fanout ~> processOutbound ~> errorSplit.in

      errorSplit.out(0) ~> toJms ~> jms
      errorSplit.out(1) ~> toError ~> error

      // TODO : Wire up the event sink
      Source.empty[FlowEnvelope] ~> event

      ClosedShape
    })

    g
  }
}
