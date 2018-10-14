package blended.streams.dispatcher

import akka.NotUsed
import akka.stream._
import akka.stream.javadsl.RunnableGraph
import akka.stream.scaladsl.GraphDSL.Implicits._
import akka.stream.scaladsl.{Broadcast, Flow, GraphDSL, Sink, Source}
import blended.container.context.api.ContainerIdentifierService
import blended.streams.FlowProcessor
import blended.streams.dispatcher.internal.ResourceTypeRouterConfig
import blended.streams.message.{FlowEnvelope, FlowMessage}
import blended.streams.processor.{HeaderTransformProcessor, LogProcessor}
import blended.util.logging.{LogLevel, Logger}

import scala.util.Success

class MissingResourceType(msg: FlowMessage)
  extends Exception(s"Missing ResourceType in [$msg] ")

class IllegalResourceType(msg : FlowMessage, rt: String)
  extends Exception(s"Illegal ResourceType [$rt] in [$msg]")

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

  private[this] val streamLogger = Logger(s"dispatcher.$dispatcherId")
  private[this] val logger = Logger[DispatcherBuilder]

  /* The inbound flow simply consumes FlowEnvelopes from the source, adds all configured default headers
   * and logs the inbound message.
   *
   * Finally it checks, whether the Resourcetype passed in with the message is configured in the config.
  */
  private val inbound : Graph[FlowShape[FlowEnvelope, FlowEnvelope], NotUsed] = {
    val defaultHeader = HeaderTransformProcessor(
      name = "defaultHeader",
      log = streamLogger,
      rules = cfg.defaultHeader.map(h => (h.name, h.value, h.overwrite)),
      idSvc = Some(idSvc)
    ).flow(logger)

    val logStart = LogProcessor("logInbound", streamLogger, LogLevel.Info).flow(logger)

    val checkResourceType = FlowProcessor.fromFunction("checkResourceType", streamLogger) { env =>

      env.flowMessage.header[String]("ResourceType") match {
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
              Success(Seq(env))
          }
      }
    }

    Flow.fromGraph(defaultHeader)
      .via(Flow.fromGraph(logStart))
      .via(Flow.fromGraph(checkResourceType))
  }

  def build(): RunnableGraph[NotUsed] = {

    val g  = RunnableGraph.fromGraph(GraphDSL.create() { implicit builder =>

      val in : Outlet[FlowEnvelope] = builder.add(source).out
      val jms : Inlet[FlowEnvelope] = builder.add(jmsOut).in
      val event : Inlet[FlowEnvelope] = builder.add(eventOut).in
      val error : Inlet[FlowEnvelope] = builder.add(errorOut).in

      val header = builder.add(inbound)

      val errorSplit = builder.add(Broadcast[FlowEnvelope](2))
      val toJms = builder.add(Flow[FlowEnvelope].filter(_.exception.isEmpty))
      val toError = builder.add(Flow[FlowEnvelope].filter(_.exception.isDefined))

      in ~> header ~> errorSplit.in

      Source.empty[FlowEnvelope] ~> event

      errorSplit.out(0) ~> toJms ~> jms
      errorSplit.out(1) ~> toError ~> error

      ClosedShape
    })

    g
  }
}
