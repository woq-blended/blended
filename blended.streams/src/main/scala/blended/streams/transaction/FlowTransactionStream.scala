package blended.streams.transaction

import akka.NotUsed
import akka.actor.{ActorRef, ActorSystem}
import akka.pattern.ask
import akka.stream.scaladsl.{Flow, GraphDSL, Keep, Sink, Source}
import akka.stream.{ActorMaterializer, FlowShape, Graph, Materializer}
import akka.util.Timeout
import blended.streams.message.FlowEnvelope
import blended.util.logging.Logger

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

class FlowTransactionStream(
  cfg : FlowHeaderConfig,
  tMgr : ActorRef,
  log: Logger,
  performSend : FlowEnvelope => Boolean,
  sendFlow : Flow[FlowEnvelope, FlowEnvelope, NotUsed],
)(implicit system: ActorSystem) {

  private case class TransactionStreamContext(
    envelope : FlowEnvelope,
    trans : Option[Future[Try[FlowTransaction]]],
    sendEnvelope : Option[Future[Try[FlowEnvelope]]]
  )

  private implicit val timeout : Timeout = Timeout(1.second)
  private implicit val eCtxt : ExecutionContext = system.dispatcher
  private implicit val materializer : Materializer = ActorMaterializer()

  // recreate the FlowTransactionEvent from the inbound envelope
  private val updateEvent : FlowEnvelope => Try[(FlowEnvelope, FlowTransactionEvent)] = { env =>
    Try {
      val event = FlowTransactionEvent.envelope2event(cfg)(env).get
      log.debug(s"Received transaction event [${event.transactionId}][${event.state}]")
      (env, event)
    }
  }

  // run the inbound transaction update through the transaction manager
  private val recordTransaction : Try[(FlowEnvelope, FlowTransactionEvent)] => TransactionStreamContext = {
    case Success((env, event)) =>
      log.debug(s"Recording transaction event [${event.transactionId}][${event.state}]")
      TransactionStreamContext(
        envelope = env,
        trans = Some((tMgr ? event).mapTo[FlowTransaction].map(s => Success(s))),
        sendEnvelope = None
      )
    case Failure(t) =>
      log.error(t)(s"Failed to record transaction [$t]")
      throw t
  }

  private val logAndPrepareSend : TransactionStreamContext => TransactionStreamContext = { in =>

    val transEnv : Future[Try[FlowEnvelope]] =
      in.trans.get.map {
        case Success(t) =>
          if (t.state == FlowTransactionState.Started || t.terminated) {
            log.info(t.toString())
          } else {
            log.debug(t.toString())
          }
          Success(FlowTransaction.transaction2envelope(cfg)(t))
        case Failure(t) =>
          log.error(t)(t.getMessage())
          Failure(t)
      }

    TransactionStreamContext(
      in.envelope,
      trans = None,
      sendEnvelope = Some(transEnv)
    )
  }

  private val sendTransaction : TransactionStreamContext => TransactionStreamContext = { in =>
    val sendEnv : Future[Try[FlowEnvelope]] = in.sendEnvelope.get.map {
      case Success(s) =>
        if (performSend(s)) {
          log.trace(s"About to send transaction envelope  [${in.envelope.id}]")
          Source.single(s).via(sendFlow).toMat(Sink.head[FlowEnvelope])(Keep.right).run()
        }
        Success(s)
      case Failure(t) =>
        log.error(t)(s"Failed to create transaction envelope for [${in.envelope.id}]")
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

      val performSend : TransactionStreamContext => Future[FlowEnvelope] = { ctxt =>
        ctxt.sendEnvelope match {
          case None =>
            Future(ctxt.envelope.withException(new IllegalStateException("Send transaction has not been called.")))
          case Some(s) => s.map {
            case Success(env) =>
              log.trace(s"Send flow for transaction [${env.id}] completed successfully.")
              ctxt.envelope.acknowledge()
              env
            case Failure(t) =>
              ctxt.envelope.withException(t)
          }
        }
      }

      val ack : Flow[TransactionStreamContext, FlowEnvelope, NotUsed] =
        Flow[TransactionStreamContext].mapAsync[FlowEnvelope](5)(performSend).named("performSend")

      val update = b.add(Flow.fromFunction(updateEvent).named("updateEvent"))
      val record = b.add(Flow.fromFunction(recordTransaction).named("recordTransaction"))
      val logTrans = b.add(Flow.fromFunction(logAndPrepareSend).named("logTransaction"))
      val sendTrans = b.add(Flow.fromFunction(sendTransaction).named("sendTransaction"))
      val acknowledge = b.add(ack)

      update ~> record ~> logTrans ~> sendTrans ~> acknowledge

      FlowShape(update.in, acknowledge.out)
    }

    Flow.fromGraph(g)
  }
}
