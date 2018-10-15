package blended.streams.dispatcher

import akka.NotUsed
import akka.stream._
import akka.stream.javadsl.RunnableGraph
import akka.stream.scaladsl.GraphDSL.Implicits._
import akka.stream.scaladsl.{Broadcast, Flow, GraphDSL, Sink, Source}
import blended.container.context.api.ContainerIdentifierService
import blended.jms.bridge.BridgeProviderConfig
import blended.jms.utils.{JmsDestination, JmsQueue}
import blended.streams.FlowProcessor
import blended.streams.dispatcher.internal._
import blended.streams.jms.JmsFlowSupport
import blended.streams.message.{BaseFlowMessage, FlowEnvelope, FlowMessage}
import blended.streams.processor.HeaderTransformProcessor
import blended.util.logging.LogLevel.LogLevel
import blended.util.logging.{LogLevel, Logger}

import scala.reflect.ClassTag
import scala.util.{Failure, Success, Try}

class MissingResourceType(msg: FlowMessage)
  extends Exception(s"Missing ResourceType in [$msg] ")

class IllegalResourceType(msg : FlowMessage, rt: String)
  extends Exception(s"Illegal ResourceType [$rt] in [$msg]")

class MissingOutboundRouting(rt: String)
  extends Exception(s"At least one Outbound route must be configured for ResourceType [$rt]")

class MissingContextObject(key: String, clazz: String)
  extends Exception(s"Missing context object [$key], expected type [$clazz]")

class JmsDestinationMissing(env: FlowEnvelope, outbound : OutboundRouteConfig)
  extends Exception(s"Unable to resolve JMS Destination for [${env.id}] in [${outbound.id}]")

object DispatcherBuilder {

  // Keys to stick objects into the FlowEnvelope context
  val appHeaderKey : String = "AppLogHeader"
  val rtConfigKey : String = classOf[ResourceTypeConfig].getSimpleName()
  val outboundCfgKey : String = classOf[OutboundRouteConfig].getSimpleName()

  val HEADER_RESOURCETYPE        = "ResourceType"

  val HEADER_BRIDGE_VENDOR       : String => String = prefix => prefix + "BridgeVendor"
  val HEADER_BRIDGE_PROVIDER     : String => String = prefix => prefix + "BridgeProvider"
  val HEADER_BRIDGE_DEST         : String => String = prefix => prefix + "BridgeDestination"

  val HEADER_CBE_ENABLED         : String => String = prefix => prefix + "CbeEnabled"

  val HEADER_EVENT_VENDOR        : String => String = prefix => prefix + "EventVendor"
  val HEADER_EVENT_PROVIDER      : String => String = prefix => prefix + "EventProvider"
  val HEADER_EVENT_DEST          : String => String = prefix => prefix + "EventDestination"
  val HEADER_OUTBOUND_ID         : String => String = prefix => prefix + "OutboundId"

  val HEADER_BRIDGE_RETRY        : String => String = prefix => prefix + "Retry"
  val HEADER_BRIDGE_RETRYCOUNT   : String => String = prefix => prefix + "BridgeRetryCount"
  val HEADER_BRIDGE_MAX_RETRY    : String => String = prefix => prefix + "BridgeMaxRetry"
  val HEADER_BRIDGE_CLOSE        : String => String = prefix => prefix + "BridgeCloseTA"

  val HEADER_TIMETOLIVE          : String => String = prefix => prefix + "TimeToLive"
}

case class DispatcherBuilder(

  dispatcherId : String,

  idSvc : ContainerIdentifierService,

  // The Dispatcher configuration
  dispatcherCfg: ResourceTypeRouterConfig,

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

  private[this] val prefix = dispatcherCfg.headerPrefix

  /*-------------------------------------------------------------------------------------------------*/
  private lazy val logEnvelope : String => LogLevel => Graph[FlowShape[FlowEnvelope, FlowEnvelope], NotUsed] = stepName => level =>

    FlowProcessor.fromFunction( stepName, streamLogger) { env =>

      Try {

        val maxRetries = env.headerWithDefault[Long](HEADER_BRIDGE_MAX_RETRY(prefix), -1)
        val retryCount = env.headerWithDefault[Long](HEADER_BRIDGE_RETRY(prefix), 0L)

        val logHeader : List[String] = env.getFromContext[List[String]](appHeaderKey) match {
          case Success(l) => l.getOrElse(List.empty)
          case Failure(_) => dispatcherCfg.applicationLogHeader
        }

        val headerString : Map[String, String] = logHeader match {
          case Nil => env.flowMessage.header.mapValues(_.value.toString)
          case l => l.map { h =>
            (h -> env.flowMessage.header.get(h).map(_.value.toString()).getOrElse("UNKNOWN"))
          }.toMap
        }

        streamLogger.log(level, s"[$retryCount / $maxRetries] : [${env.id}]:[$stepName] : [${headerString.mkString(",")}]")

        Seq(env)
      }
    }

  /*-------------------------------------------------------------------------------------------------*/
  /* The inbound flow simply consumes FlowEnvelopes from the source, adds all configured default headers
   * and logs the inbound message.
   *
   * Finally it checks, whether the Resourcetype passed in with the message is configured in the config.
  */
  private lazy val inbound : Graph[FlowShape[FlowEnvelope, FlowEnvelope], NotUsed] = {

    Flow.fromGraph(defaultHeader)
      .via(Flow.fromGraph(logEnvelope("logInbound")(LogLevel.Info)))
      .via(Flow.fromGraph(checkResourceType))
      .via(Flow.fromGraph(decideCbe))
  }

  private lazy val outbound : Graph[FlowShape[FlowEnvelope, FlowEnvelope], NotUsed] = {

    Flow.fromGraph(outboundMsg)
      .via(Flow.fromGraph(logEnvelope("logOutBound")(LogLevel.Info)))
      .via(Flow.fromGraph(routingDecider))
  }

  /*-------------------------------------------------------------------------------------------------*/
  private lazy val defaultHeader = HeaderTransformProcessor(
    name = "defaultHeader",
    log = streamLogger,
    rules = dispatcherCfg.defaultHeader.map(h => (h.name, h.value, h.overwrite)),
    idSvc = Some(idSvc)
  ).flow(logger)

  /*-------------------------------------------------------------------------------------------------*/
  private lazy val checkResourceType = FlowProcessor.fromFunction("checkResourceType", streamLogger) { env =>
    Try {
      env.header[String](HEADER_RESOURCETYPE) match {
        case None =>
          throw new MissingResourceType(env.flowMessage)
        case Some(rt) =>
          dispatcherCfg.resourceTypeConfigs.get(rt) match {
            case None =>
              throw new IllegalResourceType(env.flowMessage, rt)
            case Some(rtCfg) =>
              rtCfg.outbound match {
                case Nil =>
                  throw new MissingOutboundRouting(rt)
                case _ =>
                  Seq(env.setInContext(rtConfigKey, rtCfg))
              }
          }
      }
    }

  }

  private def withContextObject[T](key : String, env: FlowEnvelope)(f : T => Seq[FlowEnvelope])(implicit classTag: ClassTag[T]) : Seq[FlowEnvelope] = {

    env.getFromContext[T](key).get match {

      case None => // Should not be possible
        val rt = env.header[String](HEADER_RESOURCETYPE).getOrElse("UNKNOWN")
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
        val fanouts = rtCfg.outbound.map { ob =>
          env.setInContext(outboundCfgKey, ob).withHeader(HEADER_OUTBOUND_ID(prefix), ob.id).get
        }
        fanouts
      }
    }
  }

  /*-------------------------------------------------------------------------------------------------*/
  private lazy val decideCbe = FlowProcessor.fromFunction("decideCbe", streamLogger) { env =>

    Try {
      withContextObject[ResourceTypeConfig](rtConfigKey, env){ rtCfg : ResourceTypeConfig =>

        if (rtCfg.withCBE) {
          val newMsg = env.flowMessage
            .withHeader(HEADER_EVENT_VENDOR(prefix), dispatcherCfg.eventProvider.vendor).get
            .withHeader(HEADER_EVENT_PROVIDER(prefix), dispatcherCfg.eventProvider.provider).get
            .withHeader(HEADER_EVENT_DEST(prefix), dispatcherCfg.eventProvider.eventDestination.asString).get
            .withHeader(HEADER_CBE_ENABLED(prefix), true).get

          Seq(env.copy(flowMessage = newMsg))
        } else {
          Seq(env.withHeader(HEADER_CBE_ENABLED(prefix), false).get)
        }
      }
    }
  }

  /*-------------------------------------------------------------------------------------------------*/
  private lazy val outboundMsg = FlowProcessor.fromFunction("outboundMsg", streamLogger) { env =>

    val useHeaderBlock : OutboundHeaderConfig => Try[Boolean] = { oh =>
      Try {
        oh.condition match {
          // if the block does not have a condition, the header block will be used
          case None => true
          case Some(c) =>
            val use = idSvc.resolvePropertyString(c, env.flowMessage.header.mapValues(_.value)).map(_.asInstanceOf[Boolean]).get

            if (use) {
              streamLogger.info(s"Using header for [${env.id}]:[outboundMsg] block with expression [$c]")
            }
            use
        }
      }
    }

    Try {

      withContextObject[OutboundRouteConfig](outboundCfgKey, env) { outCfg =>
        var newEnv : FlowEnvelope = env
          .withHeader(HEADER_BRIDGE_MAX_RETRY(prefix), outCfg.maxRetries).get
          .withHeader(HEADER_BRIDGE_CLOSE(prefix), outCfg.autoComplete).get

        if (outCfg.timeToLive > 0) {
          newEnv = newEnv.withHeader(HEADER_TIMETOLIVE(prefix), outCfg.timeToLive).get
        }

        outCfg.outboundHeader.filter(b => useHeaderBlock(b).get).foreach { oh =>
          oh.header.foreach { case (header, value) =>
            val resolved = idSvc.resolvePropertyString(value, env.flowMessage.header.mapValues(_.value)).get
            streamLogger.debug(s"Resolved property [$header] to [$resolved]")
            newEnv = newEnv.withHeader(header, resolved).get
          }
        }

        if (outCfg.clearBody) {
          newEnv = newEnv.copy(flowMessage = BaseFlowMessage(newEnv.flowMessage.header))
        }

        Seq(newEnv.setInContext(appHeaderKey, outCfg.applicationLogHeader))
      }
    }
  }

  /*-------------------------------------------------------------------------------------------------*/
  private lazy val routingDecider = FlowProcessor.fromFunction("routingDecider", streamLogger) { env =>

    Try {
      withContextObject[OutboundRouteConfig](outboundCfgKey, env) { outCfg =>

        val provider : BridgeProviderConfig =
          (env.header[String](HEADER_BRIDGE_VENDOR(prefix)), env.header[String](HEADER_BRIDGE_PROVIDER(prefix))) match {
            case (Some(v), Some(p)) =>
              val vendor = idSvc.resolvePropertyString(v).map(_.toString()).get
              val provider = idSvc.resolvePropertyString(p).map(_.toString()).get
              ProviderResolver.getProvider(dispatcherCfg.providerRegistry, vendor, provider).get

            case (_, _) => outCfg.bridgeProvider
          }

        val dest : JmsDestination = env.header[String](HEADER_BRIDGE_DEST(prefix)) match {
          case Some(d) => JmsDestination.create(idSvc.resolvePropertyString(d).map(_.toString).get).get
          case None => outCfg.bridgeDestination match {
            case None => throw new JmsDestinationMissing(env, outCfg)
            case Some(d) => if (d == JmsQueue("replyTo")) {
              env.header[String](JmsFlowSupport.replyToHeader(prefix)).map(s => JmsDestination.create(s).get) match {
                case None => throw new JmsDestinationMissing(env, outCfg)
                case Some(r) => r
              }
            } else {
              d
            }
          }
        }

        streamLogger.info(s"Routing for [${env.id}] is [${provider.id}:${dest}]")

        Seq(env
          .withHeader(HEADER_BRIDGE_VENDOR(prefix), provider.vendor).get
          .withHeader(HEADER_BRIDGE_PROVIDER(prefix), provider.provider).get
          .withHeader(HEADER_BRIDGE_DEST(prefix), dest.asString).get
        )

      }
    }
  }

  private lazy val transactionEvent = FlowProcessor.fromFunction("startTransaction", streamLogger) { env =>
    Try {
      Seq(env)
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
      val processInbound = builder.add(inbound)

      // The fanout step will produce one envelope per outbound config of the resource
      // sent in the message. The envelope context will contain the config for the outbound
      // branch and the overall resource type config.
      val processFanout = builder.add(fanoutOutbound)

      // Finally, we define a subflow which processes each fanout messsage in turn,
      // it will generate CBE events, populate the outbound header sections and
      // finally set the external destination
      val processOutbound = builder.add(outbound)

      // Here we send a copy of the message populated with default headers to the
      // event output
      val eventSplit = builder.add(Broadcast[FlowEnvelope](2))
      val processEvent = builder.add(transactionEvent)

      // The error splitter pushes all envelopes that have an exception defined to the error sink
      // and all messages without an exception defined to the normal sink
      val errorFilter = builder.add(Broadcast[FlowEnvelope](2))
      val toJms = builder.add(Flow[FlowEnvelope].filter(_.exception.isEmpty))
      val toError = builder.add(Flow[FlowEnvelope].filter(_.exception.isDefined))

      // wire up the steps
      in ~> processInbound ~> eventSplit ~> processFanout ~> processOutbound ~> errorFilter
                              eventSplit ~> processEvent ~> event

      errorFilter ~> toJms ~> jms
      errorFilter ~> toError ~> error

      ClosedShape
    })

    g
  }
}
