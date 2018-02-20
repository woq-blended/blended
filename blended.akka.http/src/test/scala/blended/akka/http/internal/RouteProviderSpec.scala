package blended.akka.http.internal

import scala.util.Try

import org.scalatest.FreeSpec

import akka.http.scaladsl.testkit.ScalatestRouteTest
import blended.akka.http.HttpContext
import blended.akka.http.SimpleHttpContext
import blended.testsupport.pojosr.PojoSrTestHelper
import domino.DominoActivator

class RouteProviderSpec
    extends FreeSpec
    with ScalatestRouteTest
    with PojoSrTestHelper {

  val routeProvider = new RouteProvider()
  val route = routeProvider.dynamicRoute

  "The RouteProvider should" - {

    "return an about string on a GET request to the /about path" in {

      Get("/about") ~> route ~> check {
        assert(responseAs[String] === "Blended Akka Http Server")
      }

    }

    "leave GET requests to other paths unhandled" in {
      Get("/demo") ~> route ~> check {
        assert(handled === false)
      }
    }
  }

  "Inside a dynamic (OSGi) environment, the RouteProvider should" - {

    "handle comming and going HttpContext registrations" in {
      withPojoServiceRegistry { sr =>

        val serviceBundle = new DominoActivator() {
          whenBundleActive {
            routeProvider.dynamicAdapt(capsuleContext, bundleContext)
          }
        }

        val routeBundle = new DominoActivator() {
          whenBundleActive {
            import akka.http.scaladsl.server.Directives._
            val route = pathEnd {
              get {
                complete("HELLO")
              }
            }
            SimpleHttpContext("demo", route).providesService[HttpContext]
          }
        }

        val bundleContext = sr.getBundleContext()

        try {

          serviceBundle.start(bundleContext)

          // precondition
          Get("/demo") ~> route ~> check {
            assert(handled === false)
          }

          routeBundle.start(bundleContext)

          Get("/demo") ~> route ~> check {
            assert(responseAs[String] === "HELLO")
          }

          routeBundle.stop(bundleContext)

          // postcondition
          Get("/demo") ~> route ~> check {
            assert(handled === false)
          }

        } finally {
          // cleanup
          Try { serviceBundle.stop(bundleContext) }
          Try { routeBundle.stop(bundleContext) }
        }
      }
    }

  }

}