package blended.streams.transaction

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream._
import akka.stream.scaladsl.{Broadcast, Flow, GraphDSL, Merge, Zip}
import akka.util.Timeout
import blended.jms.utils.{IdAwareConnectionFactory, JmsTopic}
import blended.streams.{FlowHeaderConfig, FlowProcessor}
import blended.streams.jms.{JmsProducerSettings, JmsStreamSupport}
import blended.streams.message.{FlowEnvelope, FlowEnvelopeLogger}
import blended.util.logging.{LogLevel, Logger}

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}

/**
  * Consume transaction events from JMS and produce appropiate logging entries.
  *
  * Other parties produce [[FlowTransactionEvent]]s and send them to JMS wrapped
  * within a [[FlowEnvelope]]. The transformation between transaction events and
  * envelopes is encapsulated in the companion object of [[FlowTransactionEvent]].
  *
  * When a transaction event is received in it's envelope, the fthe following steps
  * will be executed:
  *
  * 1) forward the envelope unmodified to a JMS topic. This is an optional step
  *    that only applies if the [[internalCf]] parameter is not [[None]].
  * 2) decode the inbound envelope back into a transaction event
  * 3) use the underlying [[FlowTransactionManager]] to update the containers
  *    persistent store of known transactions
  * 4) log the transaction event
  *
  * @param internalCf if the transaction event shall be forwarded to JMS, this
  *                   the [[IdAwareConnectionFactory]] to be used for sending the
  *                   message
  * @param headerCfg  The [[FlowHeaderConfig]] used to calculate the property names
  * @param tMgr       The [[FlowTransactionManager]] used to manage the persistence of the
  *                   known transactions of the container
  * @param streamLogger a Logger to be used for any logging
  * @param system     The underlying [[ActorSystem]]
  */
class FlowTransactionStream(
  internalCf : Option[IdAwareConnectionFactory],
  headerCfg : FlowHeaderConfig,
  tMgr : FlowTransactionManager,
  streamLogger: FlowEnvelopeLogger
)(implicit system: ActorSystem) extends JmsStreamSupport {

  private implicit val timeout : Timeout = Timeout(3.seconds)
  private implicit val eCtxt : ExecutionContext = system.dispatcher
  private implicit val materializer : Materializer = ActorMaterializer()

  // recreate the FlowTransactionEvent from the inbound envelope
  private val updateEvent : FlowEnvelope => Try[FlowTransactionEvent] = { env =>
    Try {
      val event = FlowTransactionEvent.envelope2event(headerCfg)(env).get
      streamLogger.logEnv(env, LogLevel.Trace, s"Received transaction event [${event.transactionId}][${event.state}]")
      event
    }
  }

  // Create a JMS producer to perform the logging to JMS
  private val logEventToJms : IdAwareConnectionFactory => Flow[FlowEnvelope, FlowEnvelope, NotUsed] = { cf =>

    val settings : JmsProducerSettings = JmsProducerSettings(
      log = streamLogger,
      headerCfg = headerCfg,
      connectionFactory = cf,
      jmsDestination = Some(JmsTopic(s"${headerCfg.prefix}.topic.transactions")),
      clearPreviousException = true,
      logLevel = _ => LogLevel.Debug
    )

    jmsProducer("logToJms", settings, false)
  }

  // run the inbound transaction update through the transaction manager
  private val recordTransaction : Graph[FlowShape[Try[FlowTransactionEvent], Try[FlowTransaction]], NotUsed] = {

    GraphDSL.create() { implicit b =>
      import GraphDSL.Implicits._

      val callTMgr = b.add(Flow.fromFunction[FlowTransactionEvent, Try[FlowTransaction]]{e => tMgr.updateTransaction(e) })

      val errorFilter = b.add(FlowProcessor.partition[Try[FlowTransactionEvent]](_.isSuccess))

      // If the incoming trys are success
      val succ = b.add(Flow.fromFunction[Try[FlowTransactionEvent], FlowTransactionEvent](_.get))
      errorFilter.out0 ~> succ ~> callTMgr

      // if the incoming trys are failures
      val failed = b.add(Flow.fromFunction[Try[FlowTransactionEvent], Try[FlowTransaction]](evt => Failure(evt.failed.get)))
      errorFilter.out1 ~> failed

      val fanIn = b.add(Merge[Try[FlowTransaction]](2))
      callTMgr.out ~> fanIn.in(0)
      failed.out ~> fanIn.in(1)

      FlowShape(errorFilter.in, fanIn.out)
    }
  }

  private val logTransaction : Graph[FlowShape[Try[FlowTransaction], Try[FlowEnvelope]], NotUsed] = {

    GraphDSL.create() { implicit b =>

      val f = b.add(Flow.fromFunction[Try[FlowTransaction], Try[FlowEnvelope]]{
        case Success(t) =>
          t.state match {
            case FlowTransactionStateStarted | FlowTransactionStateCompleted =>
              streamLogger.underlying.info(t.toString())
            case FlowTransactionStateFailed =>
              streamLogger.underlying.warn(t.toString())
            case _ =>
              streamLogger.underlying.debug(t.toString())
          }

          Success(FlowTransaction.transaction2envelope(headerCfg)(t))
        case Failure(t) =>
          Failure(t)
      })

      FlowShape(f.in, f.out)
    }
  }

  private val createResult : ((FlowEnvelope, Try[FlowEnvelope])) => FlowEnvelope = { case (orig, env) =>
    env match {
      case Success(e) =>
        streamLogger.logEnv(e, LogLevel.Trace, s"Successfully processed transaction event [${e.id}]")
        e.withAckHandler(orig.getAckHandler).withRequiresAcknowledge(orig.requiresAcknowledge).clearException()
      case Failure(t) =>
        streamLogger.logEnv(orig, LogLevel.Trace, s"Failed to process transaction event [${orig.id}]")
        orig.withException(t)
    }
  }

  def build(): Flow[FlowEnvelope, FlowEnvelope, NotUsed] = {

    val g : Graph[FlowShape[FlowEnvelope, FlowEnvelope], NotUsed] = GraphDSL.create() { implicit b =>
      import GraphDSL.Implicits._

      // decode the incoming FlowEvent
      val decode = b.add(Flow.fromFunction(updateEvent).named("updateEvent"))

      // update the persisted transaction
      val record = b.add(Flow.fromGraph(recordTransaction).named("recordTransaction"))

      // log the transaction
      val logTrans = b.add(Flow.fromGraph(logTransaction).named("logTransaction"))

      // We need to split, so that we keep the original message in one branch and process the
      // event in the other branch
      val split = b.add(Broadcast[FlowEnvelope](2))


      val join = b.add(Zip[FlowEnvelope, Try[FlowEnvelope]])
      split.out(0) ~> join.in0

      internalCf match {
        case None =>
          split.out(1) ~> decode ~> record ~> logTrans
        case Some(cf) =>
          val logToJms = b.add(logEventToJms(cf).named("logToJms"))
          split.out(1) ~> logToJms ~> decode ~> record ~> logTrans
      }

      logTrans.out ~> join.in1

      val result = b.add(Flow.fromFunction(createResult).named("result"))
      join.out ~> result.in

      FlowShape(split.in, result.out)
    }

    Flow.fromGraph(g)
  }
}
