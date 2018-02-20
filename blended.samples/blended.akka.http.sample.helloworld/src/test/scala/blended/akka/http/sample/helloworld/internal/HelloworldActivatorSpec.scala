package blended.akka.http.sample.helloworld.internal

import org.scalatest.FreeSpec
import akka.http.scaladsl.testkit.ScalatestRouteTest
import akka.http.scaladsl.server.Directives._

class HelloworldActivatorSpec extends FreeSpec with ScalatestRouteTest {

  "The helloRoute should" - {

    val helloworld = new HelloworldActivator()
    // we prefix the test route, so that we can also match the "/" request
    val prefixRoute = pathPrefix("test") { helloworld.helloRoute }

    "return hello on a GET request to the / path" in {
      Get("/test") ~> prefixRoute ~> check {
        assert(responseAs[String] === "Hello World! (with pure route)")
      }
    }

    "return hello Me on a GET request to the /Me path" in {
      Get("/test/Me") ~> prefixRoute ~> check {
        assert(responseAs[String] === "Hello Me! (with pure route)")
      }
    }

  }

  "The explicitRoute should" - {

    val helloworld = new HelloworldActivator()
    // we prefix the test route, so that we can also match the "/" request
    val prefixRoute = pathPrefix("test") { helloworld.explicitRoute }

    "return hello on a GET request to the / path" in {
      Get("/test") ~> prefixRoute ~> check {
        assert(responseAs[String] === "Hello World! (with explicit context)")
      }
    }

    "return hello Me on a GET request to the /Me path" in {
      Get("/test/Me") ~> prefixRoute ~> check {
        assert(responseAs[String] === "Hello Me! (with explicit context)")
      }
    }

  }

}