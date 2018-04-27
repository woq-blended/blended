package blended.akka.http.proxy.internal

import org.scalatest.FreeSpec

import akka.http.scaladsl.testkit.ScalatestRouteTest
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.Http
import scala.concurrent.duration._
import scala.util.Failure
import scala.util.Success
import akka.http.scaladsl.model.StatusCodes
import scala.concurrent.Await
import akka.http.scaladsl.model.HttpResponse
import akka.util.Timeout

class ProxyRouteSpec extends FreeSpec with ScalatestRouteTest {

  private[this] val log = org.log4s.getLogger

  val localPort = 9999

  s"Test against embedded temporary localhost server on port $localPort" - {
    val testRoute = get {
      path("hello") {
        complete("Hello")
      } ~
        pathEnd {
          complete("Root")
        }
    } ~
      path("serverError") {
        complete(HttpResponse(StatusCodes.InternalServerError))
      }

    def localtest(f: Route => Unit): Unit = {
      TestServer.withServer(localPort, testRoute) {
        val proxyRoute = new ProxyRoute {
          override val actorSystem = system
          override val proxyConfig = ProxyTarget(path = "path", uri = s"http://localhost:$localPort", timeout = 2)
          override val sslContext = None
        }

        val prefixRoute = pathPrefix("test") {
          proxyRoute.proxyRoute
        }
        f(Route.seal(prefixRoute))
      }
    }

    "GET /" in {
      localtest { prefixRoute =>
        Get("/test") ~> prefixRoute ~> check {
          assert(responseAs[String] === "Root")
        }
      }
    }

    "GET /hello" in {
      localtest { prefixRoute =>
        Get("/test/hello") ~> Route.seal(prefixRoute) ~> check {
          assert(responseAs[String] === "Hello")
        }
        //    }
      }
    }
    "GET /missing" in {
      localtest { prefixRoute =>
        Get("/test/missing") ~> Route.seal(prefixRoute) ~> check {
          assert(status === StatusCodes.NotFound)
        }
      }
    }
    "GET /serverError returning a 500 (Internal Server Error) should convert to 502 (Bad Gateway)" in {
      localtest { prefixRoute =>
        Get("/test/serverError") ~> Route.seal(prefixRoute) ~> check {
          assert(status === StatusCodes.BadGateway)
        }
      }
    }
  }

  //  "live test against dns.google.com" in {
  //
  //    implicit val timeout: Timeout = Timeout(30.seconds)
  //
  //    val proxyRoute = new ProxyRoute {
  //      override val actorSystem = system
  //      override val proxyConfig = ProxyTarget(path = "path", uri = "https://dns.google.com/resolve", timeout = 30)
  //      override val sslContext = None
  //    }
  //
  //    val prefixRoute = pathPrefix("test") {
  //      proxyRoute.proxyRoute
  //    }
  //
  //    Get("/test/?name=tototec.de") ~> Route.seal(prefixRoute) ~> check {
  //      log.info("status: " + status)
  //      log.info("response: " + response)
  //      assert(handled === true)
  //    }
  //
  //  }

}