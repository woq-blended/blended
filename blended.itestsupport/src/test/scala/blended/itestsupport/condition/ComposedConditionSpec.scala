package blended.itestsupport.condition

import akka.testkit.{TestActorRef, TestProbe}
import blended.itestsupport.condition.ConditionProvider._
import blended.testsupport.TestActorSys
import org.scalatest.{Matchers, WordSpec}

import blended.itestsupport.condition.ConditionActor.CheckCondition
import blended.itestsupport.condition.ConditionActor.ConditionCheckResult

class ComposedConditionSpec extends WordSpec
  with Matchers {

  "A composed condition" should {

    "be satisfied with an empty condition list" in TestActorSys { testkit =>
      implicit val system = testkit.system
      val probe = TestProbe()

      val condition = new ParallelComposedCondition()

      val checker = TestActorRef(ConditionActor.props(cond = condition))
      checker.tell(CheckCondition, probe.ref)
      probe.expectMsg(ConditionCheckResult(List.empty, List.empty))
    }

    "be satisfied with a list of conditions that eventually satisfy" in TestActorSys { testkit =>
      implicit val system = testkit.system
      val probe = TestProbe()

      val conditions = List(alwaysTrue(), alwaysTrue(), alwaysTrue(), alwaysTrue())
      val condition = new ParallelComposedCondition(conditions.toSeq:_*)

      val checker = TestActorRef(ConditionActor.props(cond = condition))
      checker.tell(CheckCondition, probe.ref)
      probe.expectMsg(ConditionCheckResult(conditions, List.empty[Condition]))
    }

    "timeout with at least failing condition" in TestActorSys { testkit =>
      implicit val system = testkit.system
      val probe = TestProbe()

      val conditions = List(alwaysTrue(), alwaysTrue(), neverTrue(), alwaysTrue())
      val condition = new ParallelComposedCondition(conditions.toSeq:_*)

      val checker = TestActorRef(ConditionActor.props(cond = condition))
      checker.tell(CheckCondition, probe.ref)

      probe.expectMsg(ConditionCheckResult(
        conditions.filter(_.isInstanceOf[AlwaysTrue]),
        conditions.filter(_.isInstanceOf[NeverTrue])
      ))
    }
  }
}
