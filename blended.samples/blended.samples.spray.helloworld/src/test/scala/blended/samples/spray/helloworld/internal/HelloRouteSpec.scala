package blended.samples.spray.helloworld.internal

import blended.akka.OSGIActorConfig
import org.scalatest.{Matchers, WordSpec}
import org.slf4j.LoggerFactory
import spray.testkit.ScalatestRouteTest
import scala.concurrent.duration._

import scala.concurrent.Await

class HelloRouteSpec extends WordSpec with Matchers with ScalatestRouteTest with HelloService {

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
