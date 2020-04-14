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
import blended.testsupport.TestActorSys
import blended.testsupport.scalatest.LoggingFreeSpec

class HttpAvailableConditionSpec extends LoggingFreeSpec with ScalatestRouteTest {

  "The HttpAvailableCondition" - {

    "should fail with no existing HTTP server" in TestActorSys { testkit =>

      implicit val system = testkit.system
      val probe = TestProbe()

      val t = 5.seconds

      val condition = HttpAvailableCondition("http://localhost:8888/nonExisting", Some(t))

      val checker = TestActorRef(ConditionActor.props(cond = condition))
      checker.tell(CheckCondition, probe.ref)
      probe.expectMsg(t + 1.second, ConditionCheckResult(List.empty[Condition], List(condition)))

    }

    "should be satisfied with the intra JVM HTTP Server" in TestActorSys { testkit =>
      implicit val system = testkit.system
      val probe = TestProbe()

      val localPort = 9999
      val route = get {
        path("hello") {
          complete("Hello")
        }
      }

      TestServer.withServer(localPort, route) { () =>
        val t = 10.seconds

        val condition = HttpAvailableCondition(s"http://localhost:${localPort}/hello", Some(t))

        val checker = TestActorRef(ConditionActor.props(cond = condition))
        checker.tell(CheckCondition, probe.ref)

        probe.expectMsg(t, ConditionCheckResult(List(condition), List.empty[Condition]))
      }
    }

    "should not be satisfied with a wrong path on the intra JVM HTTP Server" in TestActorSys { testkit =>
      implicit val system = testkit.system
      val probe = TestProbe()

      val localPort = 9999
      val route = get {
        path("hello") {
          complete("Hello")
        }
      }

      TestServer.withServer(localPort, route) { () =>
        val t = 10.seconds

        val condition = HttpAvailableCondition(s"http://localhost:${localPort}/missing", Some(t))

        val checker = TestActorRef(ConditionActor.props(cond = condition))
        checker.tell(CheckCondition, probe.ref)

        probe.expectMsg(t, ConditionCheckResult(List.empty[Condition], List.empty[Condition]))
      }
    }

  }

}
