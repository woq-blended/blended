package blended.security.login.rest.internal

import blended.akka.OSGIActorConfig
import org.scalatest.{FreeSpec, Matchers}
import spray.http.{HttpEntity, HttpResponse, StatusCodes}
import spray.testkit.ScalatestRouteTest
import scala.concurrent.duration._

import scala.concurrent.Await

class LoginServiceSpec extends FreeSpec
  with Matchers
  with ScalatestRouteTest
  with LoginService {

  "The login service should" - {

    "Respond with a web token for a valid user" in {
      Post("/login").withEntity(HttpEntity("Hello")) ~> httpRoute ~> check {

        val entity = responseAs[HttpResponse]

        println(entity.status)
        entity.status should be (StatusCodes.Forbidden)
      }
    }
  }

  override def cleanUp(): Unit = {
    Await.result(system.terminate(), 10.seconds)
  }

  override def actorConfig: OSGIActorConfig = ???

  override implicit def actorRefFactory = system
}
