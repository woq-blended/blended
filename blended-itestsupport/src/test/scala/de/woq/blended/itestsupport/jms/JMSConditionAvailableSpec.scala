package de.woq.blended.itestsupport.jms

import akka.actor.Props
import akka.testkit.TestActorRef
import de.woq.blended.itestsupport.condition.{Condition, AsyncCondition, ConditionActor, ParallelComposedCondition}
import de.woq.blended.itestsupport.protocol._
import org.apache.activemq.ActiveMQConnectionFactory

import scala.concurrent.duration._

class JMSConditionAvailableSpec extends AbstractJMSSpec {

  "The JMS Available Condition" should {

    "fail if no connection can be made" in {
      val cf = new ActiveMQConnectionFactory("vm://foo?create=false")
      val condition = new AsyncCondition(Props(JMSChecker(cf)))

      val checker = TestActorRef(Props(ConditionActor(cond = condition)))
      checker ! CheckCondition

      expectMsg(ConditionCheckResult(List.empty[Condition], List(condition)))
    }

    "succeed if a connection can be made" in {
      val cf = new ActiveMQConnectionFactory("vm://blended?create=false")
      val condition = new AsyncCondition(Props(JMSChecker(cf)))

      val checker = TestActorRef(Props(ConditionActor(cond = condition)))
      checker ! CheckCondition

      expectMsg(ConditionCheckResult(List(condition), List.empty[Condition]))
    }
  }
}
