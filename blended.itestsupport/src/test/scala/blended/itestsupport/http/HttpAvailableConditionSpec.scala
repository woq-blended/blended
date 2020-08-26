package blended.itestsupport.http

import scala.concurrent.duration._

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.testkit.ScalatestRouteTest
import akka.testkit.TestActorRef
import akka.testkit.TestProbe
import blended.itestsupport.condition.Condition
import blended.itestsupport.condition.ConditionActor
import blended.itestsupport.condition.ConditionActor.CheckCondition
import blended.itestsupport.condition.ConditionActor.ConditionCheckResult
import blended.testsupport.scalatest.LoggingFreeSpec
import blended.testsupport.BlendedTestSupport.freePort

class HttpAvailableConditionSpec extends LoggingFreeSpec with ScalatestRouteTest {

  "The HttpAvailableCondition" - {

    "should fail with no existing HTTP server" in {
      val probe = TestProbe()

      val t = 5.seconds

      val condition = HttpAvailableCondition(s"http://localhost:$freePort/nonExisting", Some(t))

      val checker = TestActorRef(ConditionActor.props(cond = condition))
      checker.tell(CheckCondition, probe.ref)
      probe.expectMsg(t + 1.second, ConditionCheckResult(List.empty[Condition], List(condition)))

    }

    "should be satisfied with the intra JVM HTTP Server" in {
      val probe = TestProbe()

      val route = get {
        path("hello") {
          complete("Hello")
        }
      }

      TestServer.withServer(route) { port =>
        val t = 10.seconds

        val condition = HttpAvailableCondition(s"http://localhost:${port}/hello", Some(t))

        val checker = TestActorRef(ConditionActor.props(cond = condition))
        checker.tell(CheckCondition, probe.ref)

        probe.expectMsg(t, ConditionCheckResult(List(condition), List.empty[Condition]))
      }
    }

    "should not be satisfied with a wrong path on the intra JVM HTTP Server" in {
      val probe = TestProbe()

      val route = get {
        path("hello") {
          complete("Hello")
        }
      }

      TestServer.withServer(route) { port =>
        val t = 5.seconds

        val condition = HttpAvailableCondition(s"http://localhost:${port}/missing", Some(t))

        val checker = TestActorRef(ConditionActor.props(cond = condition))
        checker.tell(CheckCondition, probe.ref)

        probe.expectMsg(t + 1.second, ConditionCheckResult(List.empty[Condition], List(condition)))
      }
    }
  }
}
