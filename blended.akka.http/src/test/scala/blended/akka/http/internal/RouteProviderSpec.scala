package blended.akka.http.internal

import java.io.File

import akka.http.scaladsl.testkit.ScalatestRouteTest
import blended.akka.http.{HttpContext, SimpleHttpContext}
import blended.testsupport.BlendedTestSupport
import blended.testsupport.pojosr.PojoSrTestHelper
import domino.DominoActivator
import org.scalatest.FreeSpec

import scala.util.Try

class RouteProviderSpec
  extends FreeSpec
  with ScalatestRouteTest
  with PojoSrTestHelper {

  override def baseDir : String = new File(System.getProperty(BlendedTestSupport.projectTestOutput)).getAbsolutePath()

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
        Try {

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

    "handle HttpContext registrations with prefixes containing slashes" in {
      withPojoServiceRegistry { sr =>
        Try {

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
              SimpleHttpContext("test/demo", route).providesService[HttpContext]
            }
          }

          val bundleContext = sr.getBundleContext()

          try {

            serviceBundle.start(bundleContext)

            // precondition
            Get("/test/demo") ~> route ~> check {
              assert(handled === false)
            }

            routeBundle.start(bundleContext)

            Get("/test/demo") ~> route ~> check {
              assert(responseAs[String] === "HELLO")
            }

            routeBundle.stop(bundleContext)

            // postcondition
            Get("/test/demo") ~> route ~> check {
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

}
