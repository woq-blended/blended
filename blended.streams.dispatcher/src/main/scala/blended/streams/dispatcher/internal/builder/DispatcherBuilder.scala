package blended.streams.dispatcher.internal.builder

import akka.NotUsed
import akka.stream._
import akka.stream.javadsl.RunnableGraph
import akka.stream.scaladsl.GraphDSL.Implicits._
import akka.stream.scaladsl.{Broadcast, Flow, GraphDSL, Sink, Source}
import blended.container.context.api.ContainerIdentifierService
import blended.streams.dispatcher.internal._
import blended.streams.dispatcher.internal.worklist.WorklistStarted
import blended.streams.message.{FlowEnvelope, FlowMessage}
import blended.util.logging.Logger

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

  def createWithSources(
    idSvc : ContainerIdentifierService,
    dispatcherCfg : ResourceTypeRouterConfig,
    // Inbound messages
    source : Source[FlowEnvelope, _],

    // Messages with normal outcome to be disptached via jms
    envOut : Sink[FlowEnvelope, _],

    // Signal that the worklist for the inbound message has been started
    worklistOut : Sink[WorklistStarted, _],

    // Any errors go here
    errorOut : Sink[FlowEnvelope, _]
  ) : RunnableGraph[NotUsed] = {

    val builderSupport = new DispatcherBuilderSupport {
      override val prefix: String = dispatcherCfg.headerPrefix
      override val streamLogger: Logger = Logger("dispatcher." + dispatcherCfg.defaultProvider.inbound.asString)
    }

    RunnableGraph.fromGraph(GraphDSL.create() { implicit builder =>
      // The inbound messages
      val in : Outlet[FlowEnvelope] = builder.add(source).out
      val out : Inlet[FlowEnvelope] = builder.add(envOut).in
      val worklist : Inlet[WorklistStarted] = builder.add(worklistOut).in
      val error : Inlet[FlowEnvelope] = builder.add(errorOut).in

      val dispatcher = builder.add(DispatcherBuilder(idSvc, dispatcherCfg)(builderSupport).build())

      in ~> dispatcher.in
      dispatcher.out0 ~> out
      dispatcher.out1 ~> worklist
      dispatcher.out2 ~> error

      ClosedShape
    })

  }
}

case class DispatcherBuilder(
  idSvc : ContainerIdentifierService,
  dispatcherCfg: ResourceTypeRouterConfig
)(implicit val builderSupport : DispatcherBuilderSupport) {

  private[this] val logger = Logger[DispatcherBuilder]

  private lazy val inbound = DispatcherInbound(dispatcherCfg, idSvc)

  private lazy val startWorklist = DispatcherFanout(dispatcherCfg, idSvc).build()

  /*-------------------------------------------------------------------------------------------------*/
  def build(): Graph[FanOutShape3[FlowEnvelope, FlowEnvelope, WorklistStarted, FlowEnvelope], NotUsed] = {

    GraphDSL.create() { implicit builder =>

      // This is where we pick up the messages from the source, populate the headers
      // and perform initial checks if the message can be processed
      val processInbound = builder.add(inbound)

      // The fanout step will produce one envelope per outbound config of the resource
      // sent in the message. The envelope context will contain the config for the outbound
      // branch and the overall resource type config.
      // Each envelope will also contain the destination routing.
      val processFanout = builder.add(DispatcherFanout(dispatcherCfg, idSvc).build())

      // The error splitter pushes all envelopes that have an exception defined to the error sink
      // and all messages without an exception defined to the normal sink
      val errorFilter = builder.add(Broadcast[FlowEnvelope](2))
      val toJms = builder.add(Flow[FlowEnvelope].filter(_.exception.isEmpty))
      val toError = builder.add(Flow[FlowEnvelope].filter(_.exception.isDefined))

      // wire up the steps
      processInbound ~> processFanout.in

      processFanout.out0 ~> errorFilter ~> toJms
                            errorFilter ~> toError

      new FanOutShape3(
        processInbound.in,
        toJms.out,
        processFanout.out1,
        toError.out
      )
    }
  }
}
