package de.woq.blended.itestsupport.condition

import akka.actor.Props
import akka.testkit.TestActorRef
import de.woq.blended.testsupport.TestActorSys
import org.scalatest.{Matchers, WordSpecLike}
import scala.concurrent.duration._

import de.woq.blended.itestsupport.protocol._

class ComposedConditionSpec extends TestActorSys
  with WordSpecLike
  with Matchers
  with ConditionProvider {

  "A composed condition" should {

    "be satisfied with an empty condition list" in {
      val condition = new ParallelComposedCondition()(system, 300.millis)

      val checker = TestActorRef(Props(ConditionChecker(cond = condition)))
      checker ! CheckCondition(300.millis)
      expectMsg(ConditionSatisfied(condition :: Nil))
    }

    "be satisfied with a list of conditions that eventually satisfy" in {
      val condition = new ParallelComposedCondition(
        alwaysTrue, alwaysTrue, alwaysTrue, alwaysTrue
      )(system, 300.millis)

      val checker = TestActorRef(Props(ConditionChecker(cond = condition)))
      checker ! CheckCondition(300.millis)
      expectMsg(ConditionSatisfied(condition :: Nil))
    }

    "timeout with at least failing condition" in {
      val condition = new ParallelComposedCondition(
        alwaysTrue, alwaysTrue, neverTrue, alwaysTrue
      )(system, 300.millis)

      val checker = TestActorRef(Props(ConditionChecker(cond = condition)))
      checker ! CheckCondition(500.millis)
      expectMsg(ConditionTimeOut(condition :: Nil))
    }
  }

}
