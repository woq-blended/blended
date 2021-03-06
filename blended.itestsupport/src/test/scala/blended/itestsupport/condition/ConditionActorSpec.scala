package blended.itestsupport.condition

import akka.testkit.{TestActorRef, TestProbe}
import blended.itestsupport.condition.ConditionActor.CheckCondition
import blended.itestsupport.condition.ConditionActor.ConditionCheckResult
import blended.itestsupport.condition.ConditionProvider._
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import akka.actor.ActorSystem

class ConditionActorSpec extends AnyWordSpec
  with Matchers {

  private implicit val system : ActorSystem = ActorSystem("ConditionActor")

  "The Condition Actor" should {

    "respond with a satisfied message once the condition was satisfied" in {
      val probe = TestProbe()

      val c = alwaysTrue()
      val checker = TestActorRef(ConditionActor.props(cond = c))
      checker.tell(CheckCondition, probe.ref)
      probe.expectMsg(ConditionCheckResult(List(c), List.empty[Condition]))
    }

    "respond with a timeout message if the condition wasn't satisfied in a given timeframe" in {
      val probe = TestProbe()

      val c = neverTrue()
      val checker = TestActorRef(ConditionActor.props(cond = c))
      checker.tell(CheckCondition, probe.ref)
      probe.expectMsg(ConditionCheckResult(List.empty[Condition],List(c)))
    }

    "respond with a satisfied message if a nested parallel condition is satisfied" in {
      val probe = TestProbe()

      val pc = ParallelComposedCondition(alwaysTrue(), alwaysTrue())
      val checker = TestActorRef(ConditionActor.props(pc))

      checker.tell(CheckCondition, probe.ref)
      probe.expectMsg(ConditionCheckResult(pc.conditions.toList, List.empty[Condition]))
    }
  }
}
