package de.woq.blended.itestsupport.condition.jolokia

import akka.actor.Props
import akka.testkit.TestActorRef
import de.woq.blended.itestsupport.condition.{ConditionChecker, ParallelComposedCondition}
import de.woq.blended.itestsupport.jolokia.JolokiaAvailableCondition
import de.woq.blended.itestsupport.protocol.{ConditionSatisfied, CheckCondition}
import de.woq.blended.testsupport.TestActorSys
import org.scalatest.{Matchers, WordSpecLike}
import scala.concurrent.duration._

class JolokiaConditionSpec extends TestActorSys
  with WordSpecLike
  with Matchers {

  "The JolokiaAvailableCondition" should {

    "be satisfied with the intra JVM Jolokia" in {
      val condition = new ParallelComposedCondition(
        new JolokiaAvailableCondition(url = "http://localhost:7777/jolokia", timeout = 5.seconds)
      )(system, 10.seconds)

      val checker = TestActorRef(Props(ConditionChecker(cond = condition)))
      checker ! CheckCondition(10.seconds)
      expectMsg(10.seconds, ConditionSatisfied(condition :: Nil))
    }
  }

}
