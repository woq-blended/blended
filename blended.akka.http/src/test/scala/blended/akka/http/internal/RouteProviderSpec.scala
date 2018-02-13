package blended.akka.http.internal

import org.scalatest.FreeSpec
import akka.http.scaladsl.testkit.ScalatestRouteTest
import domino.capsule.DynamicCapsuleContext
import domino.DominoActivator
import com.sun.net.httpserver.HttpServer
import blended.akka.http.HttpContext
import blended.akka.http.SimpleHttpContext
import scala.util.Try
import scala.concurrent.Promise
import scala.concurrent.Await
import scala.concurrent.duration.Duration
import java.util.concurrent.TimeUnit

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

    "handle new HttpContext registrations" in {
      withPojoServiceRegistry { sr =>

        val activator1 = new DominoActivator() {
          whenBundleActive {
            routeProvider.dynamicAdapt(capsuleContext, bundleContext)
          }
        }

        val activator2 = new DominoActivator() {
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

        try {
          activator1.start(sr.getBundleContext())

          //          {
          //            val p1 = Promise[String]()

          // precondition
          Get("/demo") ~> route ~> check {
            assert(handled === false)
            //              p1.success("1")
          }

          //            Await.result(p1.future, Duration(1, TimeUnit.SECONDS))
          //          }

          activator2.start(sr.getBundleContext())

          //          {
          //            val p2 = Promise[String]()

          Get("/demo") ~> route ~> check {
            //              p2.success("1")
            assert(responseAs[String] === "HELLO")
          }

          //            Await.result(p2.future, Duration(1, TimeUnit.SECONDS))
          //          }

          activator2.stop(sr.getBundleContext())

          //          {
          //            val p3 = Promise[String]()
          // postcondition
          Get("/demo") ~> route ~> check {
            //              p3.success("1")
            assert(handled === false)
          }

          //            Await.result(p3.future, Duration(1, TimeUnit.SECONDS))
          //          }

        } finally {
          Try { activator1.stop(sr.getBundleContext) }
          Try { activator2.stop(sr.getBundleContext) }
        }
      }
    }

  }

}