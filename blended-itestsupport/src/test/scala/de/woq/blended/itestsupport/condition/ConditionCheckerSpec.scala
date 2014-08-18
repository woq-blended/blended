package de.woq.blended.itestsupport.condition

import akka.actor.Props
import akka.testkit.TestActorRef
import de.woq.blended.testsupport.TestActorSys
import org.scalatest.{Matchers, WordSpecLike}
import ConditionProvider._

import de.woq.blended.itestsupport.protocol._

class ConditionCheckerSpec extends TestActorSys
  with WordSpecLike
  with Matchers {


  "The Condition Checker" should {

    "respond with a satisfied message once the condition was satisfied" in {
      val c = alwaysTrue
      val checker = TestActorRef(Props(ConditionChecker(cond = c)))
      checker ! CheckCondition
      expectMsg(ConditionSatisfied(c :: Nil))
    }

    "respond with a timeout message if the condition wasn't satisfied in a given timeframe" in {
      val c = neverTrue
      val checker = TestActorRef(Props(ConditionChecker(cond = c)))
      checker ! CheckCondition
      expectMsg(ConditionTimeOut(c :: Nil))
    }
  }
}
