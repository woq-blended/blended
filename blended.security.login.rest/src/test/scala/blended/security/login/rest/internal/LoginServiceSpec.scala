package blended.security.login.rest.internal

import akka.http.scaladsl.model.{HttpEntity, HttpResponse}
import akka.http.scaladsl.testkit.ScalatestRouteTest
import org.scalatest.FreeSpec

import scala.concurrent.Await
import scala.concurrent.duration._

class LoginServiceSpec extends FreeSpec
  with ScalatestRouteTest {

  private[this] lazy val svc = new LoginService()

  "The login service should" - {

    "Respond with a web token for a valid user" in {
      Post("/login").withEntity(HttpEntity("Hello")) ~> svc.route ~> check {

        val entity = responseAs[HttpResponse]

        //entity.status should be (StatusCodes.Forbidden)

        pending
      }
    }
  }

  override def cleanUp(): Unit = {
    Await.result(system.terminate(), 10.seconds)
  }
}
