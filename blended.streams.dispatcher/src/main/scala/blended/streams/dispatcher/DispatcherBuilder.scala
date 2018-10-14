package blended.streams.dispatcher

import akka.stream._
import akka.stream.javadsl.RunnableGraph
import akka.stream.scaladsl.GraphDSL.Implicits._
import akka.stream.scaladsl.{Broadcast, Flow, GraphDSL, Sink, Source}
import blended.streams.message.FlowEnvelope

case class DispatcherBuilder(

  // Inbound messages
  source : Source[FlowEnvelope, _],

  // Messages with normal outcome to be disptached via jms
  jmsOut : Sink[FlowEnvelope, _],

  // Events to be dispatched
  eventOut : Sink[FlowEnvelope, _],

  // Any error go here
  errorOut : Sink[FlowEnvelope, _]
) {

  def build() = {

    val g  = RunnableGraph.fromGraph(GraphDSL.create() { implicit builder =>

      val in : Outlet[FlowEnvelope] = builder.add(source).out
      val jms : Inlet[FlowEnvelope] = builder.add(jmsOut).in
      val event : Inlet[FlowEnvelope] = builder.add(eventOut).in
      val error : Inlet[FlowEnvelope] = builder.add(errorOut).in

      val errorSplit = builder.add(Broadcast[FlowEnvelope](2))
      val toJms = builder.add(Flow[FlowEnvelope].filter(_.exception.isEmpty))
      val toError = builder.add(Flow[FlowEnvelope].filter(_.exception.isDefined))

      in ~> errorSplit.in

      Source.empty[FlowEnvelope] ~> event

      errorSplit.out(0) ~> toJms ~> jms
      errorSplit.out(1) ~> toError ~> error

      ClosedShape
    })

    g
  }
}
