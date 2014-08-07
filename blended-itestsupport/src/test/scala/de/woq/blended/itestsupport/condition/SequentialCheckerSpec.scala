package de.woq.blended.itestsupport.condition

import akka.actor.Props
import akka.testkit.TestActorRef
import de.woq.blended.testsupport.TestActorSys
import org.scalatest.{Matchers, WordSpecLike}
import scala.concurrent.duration._

import de.woq.blended.itestsupport.protocol._

class SequentialCheckerSpec extends TestActorSys
  with WordSpecLike
  with Matchers
  with ConditionProvider {

  "The Condition Checker" should {

    "respond with a satisfied message on an empty list of conditions" in {
      val checker = TestActorRef(Props(SequentialChecker(List.empty)))
      checker ! CheckCondition
      expectMsg(ConditionSatisfied(List.empty))
    }

    "respond with a satisfied message after a single wrapped condition has been satisfied" in {
      val conditions = (1 to 1).map { i => alwaysTrue() }.toList

      val checker = TestActorRef(Props(SequentialChecker(conditions)))
      checker ! CheckCondition

      expectMsg(ConditionSatisfied(conditions))
    }

    "respond with a satisfied message after some wrapped conditions have been satisfied" in {
      val conditions = (1 to 5).map { i => alwaysTrue() }.toList

      val checker = TestActorRef(Props(SequentialChecker(conditions)))
      checker ! CheckCondition

      expectMsg(ConditionSatisfied(conditions))
    }

    "respond with a timeout message after a single wrapped condition has timed out" in {
      val conditions = (1 to 1).map { i => neverTrue() }.toList

      val checker = TestActorRef(Props(SequentialChecker(conditions)))
      checker ! CheckCondition

      expectMsg(ConditionTimeOut(conditions))
    }

    "respond with a timeout message after the first wrapped condition has timed out" in {
      val conditions = (1 to 5).map { i => neverTrue() }.toList

      val checker = TestActorRef(Props(SequentialChecker(conditions)))
      checker ! CheckCondition

      expectMsg(ConditionTimeOut(conditions))
    }

    """"
     respond with a timeout message containing the remaining Conditions
     after the first failing condition has timed out
    """ in {

      val trueConditions      = (1 to 2).map { i => alwaysTrue() }.toList
      val remainingConditions = neverTrue() :: (1 to 2).map { i => alwaysTrue() }.toList

      val checker = TestActorRef(Props(SequentialChecker(trueConditions ::: remainingConditions)))
      checker ! CheckCondition

      expectMsg(ConditionTimeOut(remainingConditions))
    }
  }

}
