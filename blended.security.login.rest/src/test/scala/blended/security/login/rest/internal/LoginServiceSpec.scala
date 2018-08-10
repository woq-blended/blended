package blended.security.login.rest.internal

import java.io.File

import akka.actor.ActorSystem
import akka.http.scaladsl.model.{HttpEntity, HttpResponse, StatusCodes}
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.testkit.ScalatestRouteTest
import blended.akka.http.HttpContext
import blended.akka.http.internal.BlendedAkkaHttpActivator
import blended.akka.internal.BlendedAkkaActivator
import blended.security.internal.SecurityActivator
import blended.security.login.internal.LoginActivator
import blended.testsupport.BlendedTestSupport
import blended.testsupport.pojosr.{PojoSrTestHelper, SimplePojosrBlendedContainer}
import org.scalatest.{DoNotDiscover, FreeSpec}

import scala.concurrent.Await
import scala.concurrent.duration._

@DoNotDiscover
class LoginServiceSpec extends FreeSpec
  with ScalatestRouteTest
  with SimplePojosrBlendedContainer
  with PojoSrTestHelper {

  private[this] lazy val svc = new LoginService()

  def withLoginService[T](f : HttpContext => T) : T = {
    withSimpleBlendedContainer(new File(BlendedTestSupport.projectTestOutput, "container").getAbsolutePath()){sr =>
      withStartedBundles(sr)(Seq(
        "blended.akka" -> Some(() => new BlendedAkkaActivator()),
        "blended.akka.http" -> Some(() => new BlendedAkkaHttpActivator()),
        "blended.security" -> Some(() => new SecurityActivator()),
        "blended.security.login" -> Some(() => new LoginActivator()),
        "blended.security.login.rest" -> Some(() => new RestLoginActivator())
      )) { sr =>

        val ref = sr.getServiceReference(classOf[HttpContext].getName())
        val svc : HttpContext = sr.getService(ref).asInstanceOf[HttpContext]

        f(svc)
      }
    }
  }

  "The login service should" - {

    "respond with a web token for a valid user" in {

      withLoginService { sytem =>

        throw new Exception("Do something !!")

      }

    }
  }

  override def cleanUp(): Unit = {
    Await.result(system.terminate(), 10.seconds)
  }
}
