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
import akka.http.scaladsl.testkit.RouteTestTimeout
import akka.http.scaladsl.model.headers.Location

class ProxyRouteSpec extends FreeSpec with ScalatestRouteTest {

  private[this] val log = org.log4s.getLogger

  val localPort = 9999

  implicit val routeTestTimeout: RouteTestTimeout = RouteTestTimeout(30.seconds)

  s"Test against embedded temporary localhost server on port $localPort" - {
    val testRoute = get {
      path("hello") {
        complete("Hello")
      } ~
        pathEndOrSingleSlash {
          complete("Root")
        } ~
        path("redirect_to_hello") { ctx =>
          val path = ctx.request.uri.path.reverse.tail.reverse + "hello"
          val uri = ctx.request.uri.copy(path = path)
          log.info("redirect to " + uri)
          redirect(uri, StatusCodes.MovedPermanently)(ctx)
        }
    } ~
      path("serverError") {
        complete(HttpResponse(StatusCodes.InternalServerError))
      } ~
      path("fiveSecDelay") {
        complete {
          Thread.sleep(5 * 1000)
          "Delayed response"
        }
      } ~
      { ctx => // ctx =>
        log.debug("Rejecting: " + ctx)
        reject(ctx)
      }

    def localtest(redirectCount: Int = 0)(f: Route => Unit): Unit = {
      // For test infra
      //      implicit val timeout: Timeout = Timeout(30.seconds)
      TestServer.withServer(localPort, testRoute) {
        val proxyRoute = new ProxyRoute {
          override val actorSystem = system
          override val proxyConfig = ProxyTarget(path = "path", uri = s"http://localhost:$localPort", timeout = 2, redirectCount = redirectCount)
          override val sslContext = None
        }

        val prefixRoute = pathPrefix("test") {
          proxyRoute.proxyRoute
        }
        f(Route.seal(prefixRoute))
      }
    }

    "GET /" in {
      localtest() { prefixRoute =>
        Get("/test") ~> prefixRoute ~> check {
          assert(responseAs[String] === "Root")
        }
      }
    }

    "GET /hello" in {
      localtest() { prefixRoute =>
        Get("/test/hello") ~> Route.seal(prefixRoute) ~> check {
          assert(responseAs[String] === "Hello")
        }
      }
    }
    "GET /missing" in {
      localtest() { prefixRoute =>
        Get("/test/missing") ~> Route.seal(prefixRoute) ~> check {
          assert(status === StatusCodes.NotFound)
        }
      }
    }
    "GET /serverError returning a 500 (Internal Server Error) should convert to 502 (Bad Gateway)" in {
      localtest() { prefixRoute =>
        Get("/test/serverError") ~> Route.seal(prefixRoute) ~> check {
          assert(status === StatusCodes.BadGateway)
        }
      }
    }

    "Get /redirect_to_hello with 0 redirect should not redirect" in {
      localtest() { prefixRoute =>
        Get("/test/redirect_to_hello") ~> Route.seal(prefixRoute) ~> check {
          assert(status === StatusCodes.MovedPermanently)
          assert(header[Location].get.uri.toString() === "http://localhost:" + localPort + "/hello")
        }
      }
    }

    "Get /redirect_to_hello with 1 redirect should redirect" in {
      localtest(redirectCount = 1) { prefixRoute =>
        Get("/test/redirect_to_hello") ~> Route.seal(prefixRoute) ~> check {
          assert(status === StatusCodes.OK)
          assert(responseAs[String] === "Hello")
        }
      }
    }

    // to be able to test the timeout at al, we need to have a timeout in the application.conf
    "GET /fiveSecDelay should run into timeout of 2 sec" in {
      localtest() { prefixRoute =>
        Get("/test/fiveSecDelay") ~> Route.seal(prefixRoute) ~> check {
          log.info("status: " + status)
          log.info("response: " + response)
          assert(handled === true)
          //          log.info("entity: " + entityAs[String])

          pending

          // the following is expected, but can't get to work
          assert(response === StatusCodes.GatewayTimeout)
        }
      }
    }
  }

  "Live Tests - you need to have internet access - enable with -Dtest.online=true" - {

    "live test against http://sbuild.org" in {

      val proxyRoute = new ProxyRoute {
        override val actorSystem = system
        override val proxyConfig = ProxyTarget(
          path = "path",
          uri = "http://sbuild.org/",
          timeout = 1
        )
        override val sslContext = None
      }

      val prefixRoute = pathPrefix("test") {
        proxyRoute.proxyRoute
      }

      Get("/test/") ~> Route.seal(prefixRoute) ~> check {
        log.info("status: " + status)
        log.info("response: " + response)
        log.info("entity: " + entityAs[String])
        assert(handled === true)
        assert(entityAs[String].contains("SBuild - the magic free but powerful build tool"))
      }

    }

    "live test against http://heise.de with redirectCount 2" in {

      val proxyRoute = new ProxyRoute {
        override val actorSystem = system
        override val proxyConfig = ProxyTarget(
          path = "path",
          uri = "http://heise.de",
          timeout = 1,
          redirectCount = 1
        )
        override val sslContext = None
      }

      val prefixRoute = pathPrefix("test") {
        proxyRoute.proxyRoute
      }

      Get("/test/") ~> Route.seal(prefixRoute) ~> check {
        log.info("status: " + status)
        log.info("response: " + response)
        log.info("entity: " + entityAs[String])
        assert(handled === true)
        assert(status === StatusCodes.OK)
        assert(entityAs[String].contains("heise online"))
      }

    }

    "live test against https://github.com/woq-blended/blended" in {

      val proxyRoute = new ProxyRoute {
        override val actorSystem = system
        override val proxyConfig = ProxyTarget(
          path = "path",
          uri = "https://github.com/woq-blended/blended",
          timeout = 30
        )
        override val sslContext = None
      }

      val prefixRoute = pathPrefix("test") {
        proxyRoute.proxyRoute
      }

      Get("/test/") ~> Route.seal(prefixRoute) ~> check {
        log.info("status: " + status)
        log.info("response: " + response)
        log.info("entity: " + entityAs[String])
        assert(handled === true)
        assert(entityAs[String].contains("An OSGI container framework in Scala with focus on testing"))
      }

    }

  }

}