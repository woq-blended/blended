package blended.streams.transaction.internal

import akka.NotUsed
import akka.actor.{ActorRef, ActorSystem}
import akka.stream.scaladsl.{Flow, GraphDSL, Keep, Sink, Source}
import blended.streams.message.FlowEnvelope
import blended.streams.transaction.{FlowHeaderConfig, FlowTransaction, FlowTransactionEvent}
import akka.pattern.ask
import akka.stream.{ActorMaterializer, Graph, SinkShape}
import akka.util.Timeout
import blended.util.logging.Logger

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}

class FlowTransactionStream(
  cfg : FlowHeaderConfig,
  tMgr : ActorRef,
  sendFlow : Flow[FlowEnvelope, FlowEnvelope, NotUsed]
)(implicit system: ActorSystem, log: Logger) {

  private implicit val timeout = Timeout(1.second)
  private implicit val eCtxt = system.dispatcher
  private implicit val materializer = ActorMaterializer()

  // recreate the FlowTransactionEvent from the inbound envelope
  private val updateEvent : FlowEnvelope => Try[(FlowEnvelope, FlowTransactionEvent)] = { env =>
    Try {
      val event = FlowTransactionEvent.envelope2event(cfg)(env).get
      log.debug(s"Logging transaction event [${event.transactionId}]")
      (env, event)
    }
  }

  // run the inbound transaction update through the transaction manager
  private val recordTransaction : Try[(FlowEnvelope, FlowTransactionEvent)] => Future[(FlowEnvelope, FlowTransaction)] = {
    case Success((env, event)) => {
      log.debug(s"Recording transaction event [${event.transactionId}]")
      (tMgr ? event).mapTo[FlowTransaction].map { t => (env, t) }
    }
    case Failure(t) =>
      log.error(t)(s"Failed to record transaction [$t]")
      throw t
  }

  private val prepareSend : Future[(FlowEnvelope, FlowTransaction)] => Future[(FlowEnvelope, FlowEnvelope)] = { f =>
    f.map { ft => (ft._1, FlowTransaction.transaction2envelope(cfg)(ft._2)) }
  }

  private val sendTransaction : Future[(FlowEnvelope, FlowEnvelope)] => Future[(FlowEnvelope, FlowEnvelope)] = { e =>
    e.flatMap { env =>
      Source.single(env._2).via(sendFlow).toMat(Sink.head[FlowEnvelope])(Keep.right).run().map { i => (env._1, i) }
    }
  }

  private val acknowledge : Future[(FlowEnvelope, FlowEnvelope)] => Unit = { r =>
    r.onComplete {
      case Success(s) =>
        s._1.acknowledge()
      case Failure(t) =>
        log.error(t)(s"Failed to record transaction state [${t.getMessage}]")
    }
  }

  private val sink : Graph[SinkShape[Future[(FlowEnvelope, FlowEnvelope)]], NotUsed] = GraphDSL.create() { implicit b =>
    import GraphDSL.Implicits._

    val in = b.add(Flow.fromFunction(acknowledge))
    val result = Sink.ignore

    in ~> result

    SinkShape(in.in)
  }

  def build(): Sink[FlowEnvelope, NotUsed] = {
    Flow.fromFunction(updateEvent)
      .via(Flow.fromFunction(recordTransaction))
      .via(Flow.fromFunction(prepareSend))
      .via(Flow.fromFunction(sendTransaction))
      .to(sink)
  }
}
