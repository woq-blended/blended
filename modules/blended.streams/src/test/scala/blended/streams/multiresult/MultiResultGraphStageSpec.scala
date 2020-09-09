package blended.streams.multiresult

import akka.NotUsed
import akka.actor.{ActorRef, ActorSystem}
import akka.stream.scaladsl.{Flow, Source}
import akka.testkit.TestKit
import blended.streams.StreamFactories
import blended.streams.message.FlowEnvelope
import blended.testsupport.scalatest.LoggingFreeSpecLike
import org.scalatest.matchers.should.Matchers

import scala.concurrent.duration._
import scala.concurrent.Await

class MultiResultGraphStageSpec extends TestKit(ActorSystem("multiresult"))
  with LoggingFreeSpecLike
  with Matchers {

  "The multi result graph should" - {

    "create copies of the inbound message according to the provided function" in {

      val timeout : FiniteDuration = 3.seconds

      val numCopies : Int = 5
      val numMsg : Int = 3

      val createCopies : FlowEnvelope => List[FlowEnvelope] = env => List.fill(numCopies)(env)

      val copy : Flow[FlowEnvelope, FlowEnvelope, NotUsed] = Flow.fromGraph(
        new MultiResultGraphStage[FlowEnvelope, FlowEnvelope]("test")(createCopies)
      )

      val source : Source[FlowEnvelope, ActorRef] =
        StreamFactories.actorSource[FlowEnvelope](numMsg * numCopies).via(copy)

      val (actor, collector) = StreamFactories.runMatSourceWithTimeLimit[FlowEnvelope, ActorRef](
        name = "multiResult",
        source = source,
        timeout = Some(timeout),
        completeOn = Some({s : Seq[FlowEnvelope] => s.size == numCopies * numMsg})
      )

      1.to(numMsg).foreach(_ => actor ! FlowEnvelope())

      val copies : Seq[FlowEnvelope] = Await.result(collector.result, timeout + 500.millis)

      copies should have size (numMsg * numCopies)
    }
  }
}
