package blended.itestsupport.condition.jolokia

import akka.testkit.{TestActorRef, TestProbe}
import blended.itestsupport.condition.{Condition, ConditionActor}
import blended.itestsupport.jolokia.JolokiaAvailableCondition
import blended.testsupport.TestActorSys

import scala.concurrent.duration._
import blended.itestsupport.condition.ConditionActor.CheckCondition
import blended.itestsupport.condition.ConditionActor.ConditionCheckResult
import blended.jolokia.{JolokiaAddress, JolokiaClient}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import blended.testsupport.BlendedTestSupport.freePort

class JolokiaConditionSpec extends AnyWordSpec
  with Matchers {

  "The JolokiaAvailableCondition" should {

    "be satisfied with the intra JVM Jolokia" in TestActorSys { testkit =>
      implicit val system = testkit.system
      val probe = TestProbe()

      val t = 10.seconds

      val client : JolokiaClient = new JolokiaClient(JolokiaAddress(System.getProperty("jolokia.agent")))
      val condition = JolokiaAvailableCondition(client, Some(t))

      val checker = TestActorRef(ConditionActor.props(cond = condition))
      checker.tell(CheckCondition, probe.ref)

      probe.expectMsg(t, ConditionCheckResult(List(condition), List.empty[Condition]))
    }

    "fail with a not existing Jolokia" in TestActorSys { testkit =>
      implicit val system = testkit.system
      val probe = TestProbe()

      val t = 5.seconds

      val client : JolokiaClient = new JolokiaClient(JolokiaAddress(s"http://localhost:$freePort/jolokia"))
      val condition = JolokiaAvailableCondition(client, Some(t))

      val checker = TestActorRef(ConditionActor.props(cond = condition))
      checker.tell(CheckCondition, probe.ref)
      probe.expectMsg(t + 1.second, ConditionCheckResult(List.empty[Condition], List(condition)))
    }
  }
}
