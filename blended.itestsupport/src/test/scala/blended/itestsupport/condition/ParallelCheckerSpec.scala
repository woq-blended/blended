package blended.itestsupport.condition

import akka.testkit.{TestActorRef, TestProbe}
import blended.itestsupport.condition.ConditionActor.CheckCondition
import blended.itestsupport.condition.ConditionActor.ConditionCheckResult
import blended.testsupport.TestActorSys
import org.scalatest.{Matchers, WordSpec}

class ParallelCheckerSpec extends WordSpec
  with Matchers {

  "The Condition Checker" should {

    "respond with a satisfied message on an empty list of conditions" in TestActorSys { testkit =>
      implicit val system = testkit.system
      val probe = TestProbe()

      val checker = TestActorRef(ConditionActor.props(ParallelComposedCondition()))
      checker.tell(CheckCondition, probe.ref)
      probe.expectMsg(ConditionCheckResult(List.empty[Condition], List.empty[Condition]))
    }

    "respond with a satisfied message after a single wrapped condition has been satisfied" in TestActorSys { testkit =>
      implicit val system = testkit.system
      val probe = TestProbe()

      val conditions = (1 to 1).map { i => new AlwaysTrue() }.toList
      val condition = ParallelComposedCondition(conditions.toSeq:_*)

      val checker = TestActorRef(ConditionActor.props(condition))
      checker.tell(CheckCondition, probe.ref)

      probe.expectMsg(ConditionCheckResult(conditions, List.empty[Condition]))
    }

    "respond with a satisfied message after some wrapped conditions have been satisfied" in TestActorSys { testkit =>
      implicit val system = testkit.system
      val probe = TestProbe()

      val conditions = (1 to 5).map { i => new AlwaysTrue() }.toList
      val condition = ParallelComposedCondition(conditions.toSeq:_*)

      val checker = TestActorRef(ConditionActor.props(condition))
      checker.tell(CheckCondition, probe.ref)

      probe.expectMsg(ConditionCheckResult(conditions, List.empty[Condition]))
    }

    "respond with a timeout message after a single wrapped condition has timed out" in TestActorSys { testkit =>
      implicit val system = testkit.system
      val probe = TestProbe()

      val conditions = (1 to 1).map { i => new NeverTrue() }.toList
      val condition = ParallelComposedCondition(conditions.toSeq:_*)

      val checker = TestActorRef(ConditionActor.props(condition))
      checker.tell(CheckCondition, probe.ref)

      probe.expectMsg(ConditionCheckResult(List.empty[Condition], conditions))
    }

    "respond with a timeout message containing the timed out conditions only" in TestActorSys { testkit =>
      implicit val system = testkit.system
      val probe = TestProbe()

      val conditions = List(
        new AlwaysTrue(),
        new AlwaysTrue(),
        new NeverTrue(),
        new AlwaysTrue(),
        new AlwaysTrue()
      )
      val condition = ParallelComposedCondition(conditions.toSeq:_*)

      val checker = TestActorRef(ConditionActor.props(condition))
      checker.tell(CheckCondition, probe.ref)

      probe.expectMsg(ConditionCheckResult(
        conditions.filter(_.isInstanceOf[AlwaysTrue]),
        conditions.filter(_.isInstanceOf[NeverTrue])
      ))
    }
  }
}
