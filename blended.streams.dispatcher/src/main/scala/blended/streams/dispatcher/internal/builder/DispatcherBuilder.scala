package blended.streams.dispatcher.internal.builder

import akka.NotUsed
import akka.stream._
import akka.stream.javadsl.RunnableGraph
import akka.stream.scaladsl.GraphDSL.Implicits._
import akka.stream.scaladsl.{Broadcast, Flow, GraphDSL, Merge, Sink, Source}
import blended.container.context.api.ContainerIdentifierService
import blended.streams.FlowProcessor
import blended.streams.dispatcher.internal._
import blended.streams.message.{FlowEnvelope, FlowMessage}
import blended.streams.transaction.{FlowTransactionEvent, FlowTransactionFailed}
import blended.streams.worklist._
import blended.util.logging.Logger

import scala.util.{Success, Try}

class MismatchedEnvelopeException(id : String)
  extends Exception(s"Worklist event [$id] couldn't find the corresponding envelope")

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

  def fromSourceAndSinks(
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

      val dispatcher = builder.add(DispatcherBuilder(idSvc, dispatcherCfg)(builderSupport).core())

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
)(implicit val bs : DispatcherBuilderSupport) {

  private[this] val logger = Logger[DispatcherBuilder]

  def core(): Graph[FanOutShape3[FlowEnvelope, FlowEnvelope, WorklistStarted, FlowEnvelope], NotUsed] = {

    GraphDSL.create() { implicit builder =>

      // This is where we pick up the messages from the source, populate the headers
      // and perform initial checks if the message can be processed
      val processInbound = builder.add(DispatcherInbound(dispatcherCfg, idSvc))

      // The fanout step will produce one envelope per outbound config of the resource type
      // sent in the message. The envelope context will contain the config for the outbound
      // branch and the overall resource type config.
      // Each envelope will also contain the destination routing.
      // The step will also emit a WorklistStarted event if the calculation of the
      // fanout steps was successfull.
      // The step will emit an error envelope if an exception was thrown.
      val processFanout = builder.add(DispatcherFanout(dispatcherCfg, idSvc).build())

      // The error splitter pushes all envelopes that have an exception defined to the error sink
      // and all messages without an exception defined to the normal sink
      val errorSplitter = builder.add(FlowProcessor.partition[FlowEnvelope](_.exception.isEmpty))

      // wire up the steps
      processInbound ~> processFanout.in
      processFanout.out0 ~> errorSplitter.in

      new FanOutShape3(
        processInbound.in,
        errorSplitter.out0, // Normal outcome
        processFanout.out1, // WorklistStarted event
        errorSplitter.out1  // Outcome with Exception
      )
    }
  }

  // We try to process the outbound message using the injected outbound flow
  // and try to trans to resulting FlowEnvelope into a worklist.
  // If the worklist creation yields an exception, a FlowEnvelope with
  // exception will be passed downstream.
  // If after the oubound flow the envelope is marked with an exception,
  // We will generate a worklist failed event, otherwise we will generate
  // a Worklist completed event with the in bound envelope.
  def outbound(sendFlow : Flow[FlowEnvelope, FlowEnvelope, NotUsed])
    : Graph[FanOutShape2[FlowEnvelope, WorklistEvent, FlowEnvelope], NotUsed] = {

    GraphDSL.create() { implicit b =>

      val outbound = b.add(sendFlow
        .via(FlowProcessor.transform[WorklistEvent]("dispatchOut", bs.streamLogger) { env =>
          Try {
            val worklist = bs.worklist(env).get
            env.exception match {
              case None => WorklistStepCompleted(worklist = worklist, state = WorklistState.Completed)
              case Some(e) => WorklistStepCompleted(worklist = worklist, state = WorklistState.Failed)
            }
          }
        })
      )

      // This error happens if for some reason we cannot create the worklist for the worklist event
      val transformError = b.add(FlowProcessor.splitEither[FlowEnvelope, WorklistEvent]())

      outbound ~> transformError.in

      new FanOutShape2(
        outbound.in,
        transformError.out1,// A WorklistEvent signalling either Completed or Failed
        transformError.out0 // An exceptional outcome while creating the worklist
      )
    }
  }

  def worklistEventHandler() : Graph[FanOutShape2[WorklistEvent, FlowTransactionEvent, FlowEnvelope], NotUsed] = {

    def eventEnvelope (worklistEvent: WorklistEvent) : FlowEnvelope = {
      worklistEvent.worklist.items match {
        // Should not happen
        case Seq() =>
          FlowEnvelope(FlowMessage.noProps, worklistEvent.worklist.id).withException(new MismatchedEnvelopeException(worklistEvent.worklist.id))
        case s =>
          s.head match {
            case flowItem : FlowWorklistItem => flowItem.env
            // Should not happen
            case other => FlowEnvelope(FlowMessage.noProps, worklistEvent.worklist.id).withException(new MismatchedEnvelopeException(worklistEvent.worklist.id))
          }
      }
    }

    GraphDSL.create() { implicit b =>

      val split = b.add(Broadcast[WorklistEvent](2))
      val wlCompleted = b.add(Flow[WorklistEvent].filter(e => e.state == WorklistState.Completed))
      val wlTerminated = b.add(Flow[WorklistEvent].filter(e => e.state == WorklistState.TimeOut || e.state == WorklistState.Failed))

      val failTrans = b.add(Flow.fromFunction[WorklistEvent, FlowTransactionEvent]{ evt =>
        FlowTransactionFailed(transactionId = evt.worklist.id, reason = Some(new Exception("Failed to process worklist")))
      })

      val ack = b.add(
        Flow.fromFunction(eventEnvelope)
        .via(FlowProcessor.fromFunction("acknowledge", bs.streamLogger) { env =>
          if (env.requiresAcknowledge) {
            env.acknowledge()
          }
          Success(env)
        })
        .filter(_.exception.isDefined)
      )

      split.out(0) ~> wlTerminated ~> failTrans
      split.out(1) ~> wlCompleted ~> ack

      new FanOutShape2(split.in, failTrans.out, ack.out)
    }
  }

  // The dispatcher processes a stream of inbound FlowEnvelopes and generates TransactionEvents to update
  // a monitored FlowTransaction.
  //
  // This happens in the core graph :
  // 1. Generate a Transaction Started Event
  // 2. Perform the default inbound processing and fanout
  //    after this step we may end up with multiple FlowEnvelopes per inbound message
  // 3. The total of all generated fanout messages is called the worklist
  // 4. Update the open transaction with the worklist, so that it can track the individual branches.
  // 5. Any exceptions are available on the error outlet

  // This happens in the out graph:
  // 5. For each fanout message
  //    a. process the message via the outbound flow
  //    b. if processed successfully mark the worklist item as completed
  //    c. if processed with exception, mark the worklist item as failed
  //    d. for an internal error, generate mark the envelope as exceptional

  // This happens in the dispatcher :
  // 6. The worklist manager collects all worklist events, updates the worklist state and pushes the resulting state downstream
  // 7. The error handler collects all envelopes with exceptions and generates a transaction event from here
  // 8. The worklist eventhandler generates a transaction failed event for each failed worklist

  // 9. The combined transaction events are passed down stream

  def dispatcher(
    sendFlow : Flow[FlowEnvelope, FlowEnvelope, NotUsed]
  ) : Graph[FlowShape[FlowEnvelope, FlowTransactionEvent], NotUsed] = {

    GraphDSL.create() { implicit b =>
      // of course we start with the core
      val callCore = b.add(core())

      // we do need a send processor
      val callSend = b.add(outbound(sendFlow))

      // We will collect the errors here
      val error = b.add(Merge[FlowEnvelope](3))

      // We will collect Worklist Events here
      val event = b.add(Merge[WorklistEvent](2))

      // we will collect transaction events here
      val trans = b.add(Merge[FlowTransactionEvent](2))

      // the normal outcome of core goes to send
      callCore.out0 ~> callSend.in
      // events go to the event channel
      callCore.out1 ~> event
      // errors go the error channel
      callCore.out2 ~> error

      // the normal output of the sendflow are worklist events, so they go to the event channel
      val evtHandler = b.add(worklistEventHandler())

      callSend.out0 ~> evtHandler.in // Worklist events
      evtHandler.out0 ~> trans.in(0) // worklist events to FlowTransactions
      evtHandler.out1 ~> error // Error channel - only occurs when envelope matching worklist branch can't be resolved

      // if envelopes marked with an exception again got to the error channel
      callSend.out1 ~> error

      val errHandler = b.add(Flow.fromFunction[FlowEnvelope, FlowTransactionEvent] { env =>
        FlowTransactionFailed(env.id, env.exception)
      })

      // generate and collect transaction failures for exceptions
      error.out ~> errHandler ~> trans.in(1)

      FlowShape(callCore.in, trans.out)
    }
  }
}
