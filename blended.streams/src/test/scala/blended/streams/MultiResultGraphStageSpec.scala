package blended.streams

import akka.{Done, NotUsed}
import akka.actor.{ActorRef, ActorSystem}
import akka.stream.{ActorMaterializer, OverflowStrategy}
import akka.stream.scaladsl.{Flow, Keep, Sink, Source}
import akka.testkit.TestKit
import blended.streams.message.FlowEnvelope
import blended.testsupport.scalatest.LoggingFreeSpecLike
import blended.util.logging.Logger
import org.scalatest.Matchers

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}

class MultiResultGraphStageSpec extends TestKit(ActorSystem("multiresult"))
  with LoggingFreeSpecLike
  with Matchers {

  private val log : Logger = Logger[MultiResultGraphStageSpec]
  private implicit val eCtxt : ExecutionContext = system.dispatcher
  private implicit val materializer : ActorMaterializer = ActorMaterializer()

  "The multi result graph should" - {

    "create copies of the inbound message according to the provided function" in {

      val timeout : FiniteDuration = 3.seconds

      val numCopies : Int = 5
      val numMsg : Int = 3

      val createCopies : FlowEnvelope => List[FlowEnvelope] = env => List.fill(numCopies)(env)

      val copy : Flow[FlowEnvelope, FlowEnvelope, NotUsed] = Flow.fromGraph(
        new MultipleResultGraphStage[FlowEnvelope, FlowEnvelope](createCopies)
      )

      val source : Source[FlowEnvelope, ActorRef] = Source.actorRef[FlowEnvelope](numMsg, OverflowStrategy.fail)

      val (actor, copiesFut) : (ActorRef, Future[Seq[FlowEnvelope]]) = source
        .viaMat(copy)(Keep.left)
        .toMat(Sink.seq)(Keep.both)
        .run()

      akka.pattern.after(timeout, system.scheduler){
        system.stop(actor)
        Future { Done }
      }

      1.to(numMsg).foreach(_ => actor ! FlowEnvelope())

      val copies : Seq[FlowEnvelope] = Await.result(copiesFut, timeout + 500.millis)

      copies should have size (numMsg * numCopies)
    }
  }
}
