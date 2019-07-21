package blended.streams.transaction

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream._
import akka.stream.scaladsl.{Flow, GraphDSL, Keep, Sink, Source}
import akka.util.Timeout
import blended.jms.utils.{IdAwareConnectionFactory, JmsTopic}
import blended.streams.jms.{JmsProducerSettings, JmsStreamSupport}
import blended.streams.message
import blended.streams.message.FlowEnvelope
import blended.util.logging.Logger

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Promise}
import scala.util.control.NonFatal
import scala.util.{Failure, Success, Try}

class FlowTransactionStream(
  internalCf : Option[IdAwareConnectionFactory],
  headerCfg : FlowHeaderConfig,
  tMgr : FlowTransactionManager,
  streamLogger: Logger,
  performSend : FlowEnvelope => Boolean,
  sendFlow : Flow[FlowEnvelope, FlowEnvelope, NotUsed],
)(implicit system: ActorSystem) extends JmsStreamSupport {

  private case class TransactionStreamContext(
    envelope : FlowEnvelope,
    trans : Option[Try[FlowTransaction]],
    sendEnvelope : Option[Try[FlowEnvelope]]
  )

  private implicit val timeout : Timeout = Timeout(3.seconds)
  private implicit val eCtxt : ExecutionContext = system.dispatcher
  private implicit val materializer : Materializer = ActorMaterializer()

  // recreate the FlowTransactionEvent from the inbound envelope
  private val updateEvent : FlowEnvelope => Try[(FlowEnvelope, FlowTransactionEvent)] = { env =>
    Try {
      val event = FlowTransactionEvent.envelope2event(headerCfg)(env).get
      streamLogger.debug(s"Received transaction event [${event.transactionId}][${event.state}]")
      (env, event)
    }
  }

  private val logEventToJms : IdAwareConnectionFactory => Flow[FlowEnvelope, FlowEnvelope, NotUsed] = { cf =>

    val settings : JmsProducerSettings = JmsProducerSettings(
      log = streamLogger,
      headerCfg = headerCfg,
      connectionFactory = cf,
      jmsDestination = Some(JmsTopic(s"${headerCfg.prefix}.topic.transactions")),
      clearPreviousException = true
    )

    jmsProducer("logToJms", settings, false)
  }

  // run the inbound transaction update through the transaction manager
  private val recordTransaction : Try[(FlowEnvelope, FlowTransactionEvent)] => TransactionStreamContext = {
    case Success((env, event)) =>
      streamLogger.debug(s"Recording transaction event [${event.transactionId}][${event.state}]")

      val t : Try[FlowTransaction] = tMgr.updateTransaction(event)

      TransactionStreamContext(
        envelope = env,
        trans = Some(t),
        sendEnvelope = None
      )
    case Failure(t) =>
      streamLogger.error(t)(s"Failed to record transaction [$t]")
      throw t
  }

  private val logAndPrepareSend : TransactionStreamContext => TransactionStreamContext = { in =>

    val transEvent : Try[FlowEnvelope] = {
      in.trans.get match {
        case Success(t) =>
          if (t.state == FlowTransactionStateStarted || t.terminated) {
            streamLogger.info(t.toString())
          } else {
            streamLogger.debug(t.toString())
          }
          Success(FlowTransaction.transaction2envelope(headerCfg)(t))
        case Failure(t) =>
          streamLogger.error(t)(t.getMessage())
          Failure(t)
      }
    }

    in.copy(sendEnvelope = Some(transEvent))
  }

  private val sendTransaction : TransactionStreamContext => TransactionStreamContext = { in =>

    val sendEnv : Try[FlowEnvelope] = in.sendEnvelope.get match {
      case Success(s) =>
        if (performSend(s)) {
          streamLogger.debug(s"About to send transaction envelope  [${in.envelope.id}]")

          val pEnv : Promise[FlowEnvelope] = Promise[FlowEnvelope]

          val sentEnv : FlowEnvelope => FlowEnvelope = { env =>
            pEnv.success(env)
            env
          }

          // TODO : Avoid await
          val (actor, switch) = Source.actorRef[FlowEnvelope](1, OverflowStrategy.fail)
            .viaMat(sendFlow)(Keep.left)
            .viaMat(Flow.fromFunction[FlowEnvelope, FlowEnvelope](sentEnv))(Keep.left)
            .viaMat(KillSwitches.single)(Keep.both)
            .toMat(Sink.ignore)(Keep.left)
            .run()

          actor ! s

          try {
            val env : message.FlowEnvelope = Await.result(pEnv.future, timeout.duration)
            Success(env)
          } catch {
            case NonFatal(e) => Failure(e)
          } finally {
            switch.shutdown()
          }
        } else {
          Success(s)
        }
      case Failure(t) =>
        streamLogger.error(t)(s"Failed to create transaction envelope for [${in.envelope.id}]")
        Failure(t)
    }

    TransactionStreamContext(
      envelope = in.envelope,
      trans = None,
      sendEnvelope = Some(sendEnv)
    )
  }

  def build(): Flow[FlowEnvelope, FlowEnvelope, NotUsed] = {

    val g : Graph[FlowShape[FlowEnvelope, FlowEnvelope], NotUsed] = GraphDSL.create() { implicit b =>
      import GraphDSL.Implicits._

      val performSend : TransactionStreamContext => FlowEnvelope = { ctxt =>
        ctxt.sendEnvelope match {
          case None =>
            ctxt.envelope.withException(new IllegalStateException("Send transaction has not been called."))
          case Some(s) => s match {
            case Success(env) =>
              streamLogger.debug(s"Send flow for transaction [${env.id}] completed successfully.")
              ctxt.envelope.acknowledge()
              env
            case Failure(t) =>
              ctxt.envelope.withException(t)
          }
        }
      }

      val ack : Flow[TransactionStreamContext, FlowEnvelope, NotUsed] =
        Flow[TransactionStreamContext].map[FlowEnvelope](performSend).named("performSend")

      val update = b.add(Flow.fromFunction(updateEvent).named("updateEvent"))
      val record = b.add(Flow.fromFunction(recordTransaction).named("recordTransaction"))
      val logTrans = b.add(Flow.fromFunction(logAndPrepareSend).named("logTransaction"))
      val sendTrans = b.add(Flow.fromFunction(sendTransaction).named("sendTransaction"))
      val acknowledge = b.add(ack)

      val in : Inlet[FlowEnvelope] = internalCf match {
        case None =>
          update ~> record ~> logTrans ~> sendTrans ~> acknowledge
          update.in
        case Some(cf) =>
          val logToJms = b.add(logEventToJms(cf).named("logToJms"))
          logToJms ~> update ~> record ~> logTrans ~> sendTrans ~> acknowledge
          logToJms.in
      }

      FlowShape(in, acknowledge.out)
    }

    Flow.fromGraph(g)
  }
}
