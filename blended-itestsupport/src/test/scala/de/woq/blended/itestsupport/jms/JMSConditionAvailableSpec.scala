package de.woq.blended.itestsupport.jms

import akka.actor.Props
import akka.testkit.TestActorRef
import de.woq.blended.itestsupport.condition.{ConditionActor, ParallelComposedCondition}
import de.woq.blended.itestsupport.protocol._
import org.apache.activemq.ActiveMQConnectionFactory

import scala.concurrent.duration._

class JMSConditionAvailableSpec extends AbstractJMSSpec {

  "The JMS Available Condition" should {

    "fail if no connection can be made" in {

      val cf = new ActiveMQConnectionFactory("vm://foo?create=false")
      val condition = new ParallelComposedCondition(
        new JMSAvailableCondition(cf, 3.seconds)
      )

      val checker = TestActorRef(Props(ConditionActor(cond = condition)))
      checker ! CheckCondition
      expectMsg(5.seconds, ConditionTimeOut(condition :: Nil))
    }

    "succeed if a connection can be made" in {

      val cf = new ActiveMQConnectionFactory("vm://blended?create=false")
      val condition = new ParallelComposedCondition(
        new JMSAvailableCondition(cf, 3.seconds)
      )

      val checker = TestActorRef(Props(ConditionActor(cond = condition)))
      checker ! CheckCondition
      expectMsg(5.seconds, ConditionSatisfied(condition :: Nil))
    }
  }
}
