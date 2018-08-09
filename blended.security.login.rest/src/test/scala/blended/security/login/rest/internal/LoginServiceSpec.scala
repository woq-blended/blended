package blended.security.login.rest.internal

import java.io.File

import akka.http.scaladsl.testkit.ScalatestRouteTest
import blended.akka.http.HttpContext
import blended.akka.http.internal.BlendedAkkaHttpActivator
import blended.akka.internal.BlendedAkkaActivator
import blended.security.BlendedPermissions
import blended.security.internal.SecurityActivator
import blended.security.login.internal.LoginActivator
import blended.testsupport.BlendedTestSupport
import blended.testsupport.pojosr.{PojoSrTestHelper, SimplePojosrBlendedContainer}
import com.softwaremill.sttp.akkahttp.AkkaHttpBackend
import org.scalatest.{FreeSpec, Matchers}

import scala.concurrent.Await
import scala.concurrent.duration._
import com.softwaremill.sttp._

class LoginServiceSpec extends FreeSpec
  with ScalatestRouteTest
  with Matchers
  with SimplePojosrBlendedContainer
  with PojoSrTestHelper {

  private[this] implicit val backend = AkkaHttpBackend()


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

    "Respond with Unauthorized used without credentials" in {

      withLoginService { sytem =>

        val request = sttp.post(uri"http://localhost:9995/login/")
        val response = request.send()

        val r = Await.result(response, 3.seconds)

        r.code should be (StatusCodes.Unauthorized)
      }

    }

    "Respond with Unauthorized used with wrong credentials" in {

      withLoginService { sytem =>

        val request = sttp.post(uri"http://localhost:9995/login/").auth.basic("andreas", "foo")
        val response = request.send()

        val r = Await.result(response, 3.seconds)

        r.code should be (StatusCodes.Unauthorized)
      }

    }

    "Respond with Ok if called with correct credentials" in {

      withLoginService { sytem =>

        val request = sttp.post(uri"http://localhost:9995/login/").auth.basic("andreas", "mysecret")
        val response = request.send()

        val r = Await.result(response, 3.seconds)

        r.code should be (StatusCodes.Ok)
        val json : String = r.body.right.get2
        val permissions : BlendedPermissions = BlendedPermissions.fromJson(json).get

        permissions.granted.size should be (2)
        permissions.granted.find(_.permissionClass == Some("admins")) should be (defined)
        permissions.granted.find(_.permissionClass == Some("blended")) should be (defined)
      }

    }
  }

  override def cleanUp(): Unit = {
    backend.close()
    Await.result(system.terminate(), 10.seconds)
  }
}
