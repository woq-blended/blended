package blended.streams.dispatcher

import akka.NotUsed
import akka.stream._
import akka.stream.javadsl.RunnableGraph
import akka.stream.scaladsl.GraphDSL.Implicits._
import akka.stream.scaladsl.{Broadcast, Flow, GraphDSL, Sink, Source}
import blended.container.context.api.ContainerIdentifierService
import blended.streams.dispatcher.internal.ResourceTypeRouterConfig
import blended.streams.message.FlowEnvelope
import blended.streams.processor.HeaderTransformProcessor

case class DispatcherBuilder(

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

  private val defaultHeader : Graph[FlowShape[FlowEnvelope, FlowEnvelope], NotUsed] = {

    val noOverwrite = HeaderTransformProcessor(
      name = "headerNoOverwrite",
      rules = cfg.defaultHeader.filter(!_.overwrite).map(h => (h.name, h.value)),
      overwrite = false,
      idSvc = Some(idSvc)
    ).flow

    val overwrite = HeaderTransformProcessor(
      name = "headerNoOverwrite",
      rules = cfg.defaultHeader.filter(_.overwrite).map(h => (h.name, h.value)),
      overwrite = true,
      idSvc = Some(idSvc)
    ).flow

    Flow.fromGraph(noOverwrite).via(Flow.fromGraph(overwrite))
  }

  def build() = {

    val g  = RunnableGraph.fromGraph(GraphDSL.create() { implicit builder =>

      val in : Outlet[FlowEnvelope] = builder.add(source).out
      val jms : Inlet[FlowEnvelope] = builder.add(jmsOut).in
      val event : Inlet[FlowEnvelope] = builder.add(eventOut).in
      val error : Inlet[FlowEnvelope] = builder.add(errorOut).in

      val header = builder.add(defaultHeader)

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
