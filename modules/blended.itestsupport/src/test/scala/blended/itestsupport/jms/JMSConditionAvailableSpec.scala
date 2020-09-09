package blended.itestsupport.jms

import akka.actor.ActorSystem
import akka.testkit.{TestActorRef, TestProbe}
import blended.itestsupport.condition.ConditionActor
import blended.itestsupport.condition.ConditionActor.CheckCondition
import blended.itestsupport.condition.ConditionActor.ConditionCheckResult
import blended.jms.utils.{IdAwareConnectionFactory, SimpleIdAwareConnectionFactory}
import org.apache.activemq.ActiveMQConnectionFactory
import scala.concurrent.duration._

class JMSConditionAvailableSpec extends AbstractJMSSpec {

  private implicit val system : ActorSystem = ActorSystem("JMSAvailable")

  "The JMS Available Condition" should {

    "fail if no connection can be made" in {
      val probe : TestProbe = TestProbe()

      val cf : IdAwareConnectionFactory = SimpleIdAwareConnectionFactory(
        vendor = "amq",
        provider = "fail",
        cf = new ActiveMQConnectionFactory("vm://foo?create=false"),
        clientId = "test",
        minReconnect = 1.second
      )
      val condition = JMSAvailableCondition(cf)

      val checker = TestActorRef(ConditionActor.props(cond = condition))
      checker.tell(CheckCondition, probe.ref)

      probe.expectMsg(ConditionCheckResult(List.empty, List(condition)))
    }

    "succeed if a connection can be made" in {
      val probe : TestProbe = TestProbe()

      val cf : IdAwareConnectionFactory = SimpleIdAwareConnectionFactory(
        vendor = "amq",
        provider = "succeed",
        cf = new ActiveMQConnectionFactory("vm://blended?create=false"),
        clientId = "test",
        minReconnect = 1.second
      )

      val condition = JMSAvailableCondition(cf)

      val checker = TestActorRef(ConditionActor.props(cond = condition))
      checker.tell(CheckCondition, probe.ref)

      probe.expectMsg(ConditionCheckResult(List(condition), List.empty))
    }
  }
}
