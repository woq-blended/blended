package blended.samples.spray.helloworld.internal

import org.scalatest.{Matchers, WordSpec}
import org.slf4j.LoggerFactory
import spray.testkit.ScalatestRouteTest

class HelloRouteSpec extends WordSpec with Matchers with ScalatestRouteTest with HelloService {

  val log = LoggerFactory.getLogger(classOf[HelloRouteSpec])

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
