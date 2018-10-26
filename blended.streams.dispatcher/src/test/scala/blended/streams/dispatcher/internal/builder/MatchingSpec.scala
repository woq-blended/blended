package blended.streams.dispatcher.internal.builder

import akka.actor.ActorSystem
import akka.testkit.{TestKit, TestProbe}
import blended.streams.testsupport.CollectingActor
import blended.streams.testsupport.CollectingActor.Completed
import blended.streams.worklist.{Worklist, WorklistEvent, WorklistStarted}
import blended.testsupport.scalatest.LoggingFreeSpecLike
import org.scalatest.Matchers

class MatchingSpec extends TestKit(ActorSystem("collecting"))
  with LoggingFreeSpecLike
  with Matchers {

  "A Worklist Started should match against WorklistEvent" in {

    val p = TestProbe()
    val actor = system.actorOf(CollectingActor.props[WorklistEvent]("test", p.ref))

    actor ! WorklistStarted(worklist = Worklist("dummy", Seq.empty))
    actor ! Completed

    p.expectMsgType[List[WorklistEvent]] should have size(1)

  }

}
