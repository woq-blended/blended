package blended.streams.dispatcher.internal.builder

import akka.NotUsed
import akka.stream._
import akka.stream.scaladsl.GraphDSL.Implicits._
import akka.stream.scaladsl.{Flow, GraphDSL, Merge}
import blended.container.context.api.ContainerIdentifierService
import blended.jms.bridge.BridgeProviderConfig
import blended.streams.FlowProcessor
import blended.streams.dispatcher.internal._
import blended.streams.message.{FlowEnvelope, FlowMessage}
import blended.streams.transaction.{FlowTransactionCompleted, FlowTransactionEvent, FlowTransactionFailed, FlowTransactionUpdate}
import blended.streams.worklist._
import blended.util.logging.Logger

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

case class DispatcherBuilder(
  idSvc : ContainerIdentifierService,
  dispatcherCfg: ResourceTypeRouterConfig
)(implicit val bs : DispatcherBuilderSupport) {

  private[this] val logger = Logger[DispatcherBuilder]

  def core(): Graph[FanOutShape3[FlowEnvelope, FlowEnvelope, WorklistEvent, FlowEnvelope], NotUsed] = {

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

      val outbound = b.add(sendFlow)

      val toWorklist = b.add(Flow.fromFunction[FlowEnvelope, Either[FlowEnvelope, WorklistEvent]]{ env =>
        try {
          val worklist = bs.worklist(env).get
          val event : WorklistEvent = env.exception match {
            case None => WorklistStepCompleted(worklist = worklist, state = WorklistState.Completed)
            case Some(e) => WorklistStepCompleted(worklist = worklist, state = WorklistState.Failed)
          }

          Right(event)
        } catch {
          case t : Throwable => Left(env.withException(t))
        }

      })

      // This error happens if for some reason we cannot create the worklist for the worklist event
      val transformError = b.add(FlowProcessor.splitEither[FlowEnvelope, WorklistEvent]())

      outbound ~> toWorklist ~> transformError.in

      new FanOutShape2(
        outbound.in,
        transformError.out1,// A WorklistEvent signalling either Completed or Failed
        transformError.out0 // An exceptional outcome while creating the worklist
      )
    }
  }

  private[builder] def eventEnvelopes (worklistEvent: WorklistEvent) : Seq[FlowEnvelope] = {
    worklistEvent.worklist.items match {
      // Should not happen
      case Seq() =>
        Seq(FlowEnvelope(FlowMessage.noProps, worklistEvent.worklist.id).withException(new MismatchedEnvelopeException(worklistEvent.worklist.id)))
      case s =>
        s.map { item => item match {
          case flowItem : FlowWorklistItem => flowItem.env
          // Should not happen
          case other => FlowEnvelope(FlowMessage.noProps, worklistEvent.worklist.id).withException(new MismatchedEnvelopeException(worklistEvent.worklist.id))
        }}
    }
  }

  private[builder] def isInternalOnly(event: WorklistEvent) : Boolean = {

    val provider : Seq[Option[Boolean]] = eventEnvelopes(event).map { env =>
      env.header[Boolean](bs.headerAutoComplete)
    }

    provider.forall(c => c.getOrElse(false))
  }

  private[builder] def transactionUpdate(event: WorklistEvent) : (WorklistEvent, Option[FlowTransactionEvent]) = {
    val transEvent : Option[FlowTransactionEvent] = event match {
      // The started event will just update the FlowTransaction with a new worklist
      case started : WorklistStarted =>
        Some(FlowTransactionUpdate(
          transactionId = started.worklist.id,
          updatedState = WorklistState.Started,
          envelopes = eventEnvelopes(event):_*
        ))
      // Worklist Termination does nothing for completed worklists,
      // for failed worklists it produces a Transaction failed update
      case term : WorklistTerminated =>
        if (term.state == WorklistState.Completed) {
          if (isInternalOnly(event)) {
            Some(FlowTransactionCompleted(event.worklist.id))
          } else {
            None
          }
        } else {
          Some(FlowTransactionFailed(event.worklist.id, term.reason))
        }
      // Completed worklist steps do nothing
      case step : WorklistStepCompleted => None
    }

    (event, transEvent)
  }

  private[builder] def acknowledge(event : WorklistEvent): FlowEnvelope = {

    eventEnvelopes(event) match {
      case Seq() =>
        FlowEnvelope(FlowMessage.noProps, event.worklist.id).withException(new MismatchedEnvelopeException(event.worklist.id))
      case h :: _ =>
        if (h.requiresAcknowledge) {
          logger.debug(s"Acknowledging envelope [${h.id}]")
          h.acknowledge()
        }
        h
    }
  }

  def worklistEventHandler() : Graph[FanOutShape2[WorklistEvent, FlowTransactionEvent, FlowEnvelope], NotUsed] = {

    GraphDSL.create() { implicit b =>

      // The worklist Manager will track currently open worklist events and emit accumulated Worklist Events
      // The worklist manager will produce Worklist started and Worklist Terminated events
      val wlManager = b.add(WorklistManager.flow("dispatcher", bs.streamLogger))

      // We process the outcome of the worklist manager and transform it into Transaction events
      val processEvent = b.add(Flow.fromFunction[WorklistEvent, (WorklistEvent, Option[FlowTransactionEvent])](transactionUpdate))

      // if processEvent did come back with a TransactionEvent, this will be passed downstream
      val processFilter = b.add(FlowProcessor.partition[(WorklistEvent, Option[FlowTransactionEvent])](_._2.isDefined))

      val processWithTrans = b.add(Flow[(WorklistEvent, Option[FlowTransactionEvent])]
        .map(_._2)
        .map(_.get)
      )

      // For completed worklist we will acknowledge the envelope and capture exceptions
      val processSinTrans = b.add(
        Flow[(WorklistEvent, Option[FlowTransactionEvent])]
        .map(_._1)
        .filter(_.state == WorklistState.Completed)
        .map(acknowledge)
        .filter(_.exception.isDefined)
      )

      wlManager ~> processEvent ~> processFilter.in

      processFilter.out0 ~> processWithTrans
      processFilter.out1 ~> processSinTrans

      new FanOutShape2(wlManager.in, processWithTrans.out, processSinTrans.out)
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
      // The worklist started event goes to the event channel
      callCore.out1 ~> event
      // errors go the error channel
      callCore.out2 ~> error

      // the normal output of the sendflow are worklist events, so they go to the event channel
      val evtHandler = b.add(worklistEventHandler())
      callSend.out0 ~> event // Worklist events

      // All worklist events will go to the worklist manager, so that we can track worklist completions or failures
      event ~> evtHandler.in

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
