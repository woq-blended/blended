package de.wayofquality.blended.itestsupport.jms

import akka.actor.Props
import akka.testkit.TestActorRef
import de.wayofquality.blended.itestsupport.condition.ConditionActor
import de.wayofquality.blended.itestsupport.protocol._
import org.apache.activemq.ActiveMQConnectionFactory

class JMSConditionAvailableSpec extends AbstractJMSSpec {

  "The JMS Available Condition" should {

    "fail if no connection can be made" in {
      val cf = new ActiveMQConnectionFactory("vm://foo?create=false")
      val condition = JMSAvailableCondition(cf)

      val checker = TestActorRef(Props(ConditionActor(cond = condition)))
      checker ! CheckCondition

      expectMsg(ConditionCheckResult(List.empty, List(condition)))
    }

    "succeed if a connection can be made" in {
      val cf = new ActiveMQConnectionFactory("vm://blended?create=false")
      val condition = JMSAvailableCondition(cf)

      val checker = TestActorRef(Props(ConditionActor(cond = condition)))
      checker ! CheckCondition

      expectMsg(ConditionCheckResult(List(condition), List.empty))
    }
  }
}
