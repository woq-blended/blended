package blended.itestsupport.condition

import akka.actor.ActorSystem
import akka.testkit.{TestActorRef, TestProbe}
import blended.itestsupport.condition.ConditionProvider._
import blended.testsupport.TestActorSys
import blended.itestsupport.condition.ConditionActor.CheckCondition
import blended.itestsupport.condition.ConditionActor.ConditionCheckResult
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class ComposedConditionSpec extends AnyWordSpec
  with Matchers {

  "A composed condition" should {

    "be satisfied with an empty condition list" in TestActorSys { testkit =>
      implicit val system : ActorSystem = testkit.system
      val probe = TestProbe()

      val condition = ParallelComposedCondition()

      val checker = TestActorRef(ConditionActor.props(cond = condition))
      checker.tell(CheckCondition, probe.ref)
      probe.expectMsg(ConditionCheckResult(List.empty, List.empty))
    }

    "be satisfied with a list of conditions that eventually satisfy" in TestActorSys { testkit =>
      implicit val system : ActorSystem = testkit.system
      val probe = TestProbe()

      val conditions = List(alwaysTrue(), alwaysTrue(), alwaysTrue(), alwaysTrue())
      val condition = ParallelComposedCondition(conditions:_*)

      val checker = TestActorRef(ConditionActor.props(cond = condition))
      checker.tell(CheckCondition, probe.ref)
      probe.expectMsg(ConditionCheckResult(conditions, List.empty[Condition]))
    }

    "timeout with at least failing condition" in TestActorSys { testkit =>
      implicit val system : ActorSystem = testkit.system
      val probe = TestProbe()

      val conditions = List(alwaysTrue(), alwaysTrue(), neverTrue(), alwaysTrue())
      val condition = ParallelComposedCondition(conditions:_*)

      val checker = TestActorRef(ConditionActor.props(cond = condition))
      checker.tell(CheckCondition, probe.ref)

      probe.expectMsg(ConditionCheckResult(
        conditions.filter(_.isInstanceOf[AlwaysTrue]),
        conditions.filter(_.isInstanceOf[NeverTrue])
      ))
    }
  }
}
