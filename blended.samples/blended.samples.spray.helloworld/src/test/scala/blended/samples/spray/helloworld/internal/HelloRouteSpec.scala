package blended.samples.spray.helloworld.internal

import scala.concurrent.Await
import scala.concurrent.duration.DurationInt

import org.scalatest.Matchers
import org.scalatest.WordSpec
import org.slf4j.LoggerFactory

import blended.akka.OSGIActorConfig
import blended.security.spray.DummyBlendedSecuredRoute
import spray.testkit.ScalatestRouteTest

class HelloRouteSpec
    extends WordSpec
    with Matchers
    with ScalatestRouteTest
    with HelloService
    with DummyBlendedSecuredRoute {

  val log = LoggerFactory.getLogger(classOf[HelloRouteSpec])

  override def actorConfig: OSGIActorConfig = ???

  override def cleanUp(): Unit = Await.result(system.terminate(), 10.seconds)

  def actorRefFactory = system

  "The hello service" should {

    "return a hello message for a GET request to /hello" in {
      Get("/hello") ~> httpRoute ~> check {
        responseAs[String] should include("within OSGi")
      }
    }

    "leave GET to other paths unhandled" in {
      Get("/foo") ~> httpRoute ~> check {
        handled should be(false)
      }
    }
  }

}
