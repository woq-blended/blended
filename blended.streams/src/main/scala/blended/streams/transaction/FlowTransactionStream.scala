package blended.streams.transaction

import akka.NotUsed
import akka.actor.{ActorRef, ActorSystem}
import akka.pattern.ask
import akka.stream.scaladsl.{Flow, Keep, Sink, Source}
import akka.stream.{ActorMaterializer, Materializer}
import akka.util.Timeout
import blended.streams.message.FlowEnvelope
import blended.util.logging.Logger

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

class FlowTransactionStream(
  cfg : FlowHeaderConfig,
  tMgr : ActorRef,
  log: Logger,
  sendFlow : Flow[FlowEnvelope, FlowEnvelope, NotUsed]
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

  private val prepareSend : TransactionStreamContext => TransactionStreamContext = { in =>
    val transEnv : Future[Try[FlowEnvelope]] =
      in.trans.get.map {
        case Success(t) => Success(FlowTransaction.transaction2envelope(cfg)(t))
        case Failure(t) => Failure(t)
      }

    TransactionStreamContext(
      in.envelope,
      trans = None,
      sendEnvelope = Some(transEnv)
    )
  }

  private val sendTransaction : TransactionStreamContext => TransactionStreamContext = { in =>
    val sendEnv = in.sendEnvelope.get.map {
      case Success(s) =>
        log.trace(s"About to send transaction envelope  [${in.envelope.id}]")
        Source.single(s).via(sendFlow).toMat(Sink.head[FlowEnvelope])(Keep.right).run()
        Success(s)
      case Failure(t) => Failure(t)
    }

    TransactionStreamContext(
      envelope = in.envelope,
      trans = None,
      sendEnvelope = Some(sendEnv)
    )
  }

  private val acknowledge : TransactionStreamContext => FlowEnvelope = { in =>

    val f : Future[Try[FlowEnvelope]] = in.sendEnvelope.get.map {
      case Success(s) =>
        in.envelope.acknowledge()
        Success(s)
      case Failure(t) => Failure(t)
    }

    //TODO: review not to block here
    val result = Await.result(f, 1.second)

    result match {
      case Success(r) => r
      case Failure(t) => in.envelope.withException(t)
    }
  }

  def build(): Flow[FlowEnvelope, FlowEnvelope, NotUsed] = {
    Flow.fromFunction(updateEvent)
      .via(Flow.fromFunction(recordTransaction))
      .via(Flow.fromFunction(prepareSend))
      .via(Flow.fromFunction(sendTransaction))
      .via(Flow.fromFunction(acknowledge))
  }
}
