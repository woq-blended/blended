package blended.security.login.rest.internal

import java.io.File
import java.security.spec.X509EncodedKeySpec
import java.security.{KeyFactory, PublicKey}

import akka.http.scaladsl.testkit.ScalatestRouteTest
import blended.akka.http.HttpContext
import blended.akka.http.internal.BlendedAkkaHttpActivator
import blended.akka.internal.BlendedAkkaActivator
import blended.security.internal.SecurityActivator
import blended.security.login.api.Token
import blended.security.login.impl.LoginActivator
import blended.testsupport.BlendedTestSupport
import blended.testsupport.pojosr.{PojoSrTestHelper, SimplePojosrBlendedContainer}
import com.softwaremill.sttp._
import com.softwaremill.sttp.akkahttp.AkkaHttpBackend
import org.scalatest.{FreeSpec, Matchers}
import sun.misc.BASE64Decoder

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.Try

class LoginServiceSpec
    extends FreeSpec
    with ScalatestRouteTest
    with Matchers
    with SimplePojosrBlendedContainer
    with PojoSrTestHelper {

  private[this] implicit val backend = AkkaHttpBackend()

  def withLoginService[T](f: HttpContext => T): T = {
    withSimpleBlendedContainer(
      new File(BlendedTestSupport.projectTestOutput, "container").getAbsolutePath()) { sr =>
      withStartedBundles(sr)(
        Seq(
          "blended.akka"                -> Some(() => new BlendedAkkaActivator()),
          "blended.akka.http"           -> Some(() => new BlendedAkkaHttpActivator()),
          "blended.security"            -> Some(() => new SecurityActivator()),
          "blended.security.login"      -> Some(() => new LoginActivator()),
          "blended.security.login.rest" -> Some(() => new RestLoginActivator())
        )) { sr =>
        val ref              = sr.getServiceReference(classOf[HttpContext].getName())
        val svc: HttpContext = sr.getService(ref).asInstanceOf[HttpContext]

        f(svc)
      }
    }
  }

  def serverKey: Try[PublicKey] = Try {

    val request  = sttp.get(uri"http://localhost:9995/login/key")
    val response = request.send()

    val r = Await.result(response, 3.seconds)
    r.code should be(StatusCodes.Ok)

    val rawString = r.body.right.get
      .replace("-----BEGIN PUBLIC KEY-----\n", "")
      .replace("-----END PRIVATE KEY-----", "")
      .replaceAll("\n", "")

    val bytes = new BASE64Decoder().decodeBuffer(rawString)
    val x509  = new X509EncodedKeySpec(bytes)
    val kf    = KeyFactory.getInstance("RSA")
    kf.generatePublic(x509)
  }

  "The login service should" - {

    "Respond with Unauthorized used without credentials" in {

      withLoginService { _ =>
        val request  = sttp.post(uri"http://localhost:9995/login/")
        val response = request.send()

        val r = Await.result(response, 3.seconds)

        r.code should be(StatusCodes.Unauthorized)
      }

    }

    "Respond with Unauthorized used with wrong credentials" in {

      withLoginService { _ =>
        val request  = sttp.post(uri"http://localhost:9995/login/").auth.basic("andreas", "foo")
        val response = request.send()

        val r = Await.result(response, 3.seconds)

        r.code should be(StatusCodes.Unauthorized)
      }

    }

    "Respond with Ok if called with correct credentials" in {

      withLoginService { _ =>
        val key: PublicKey = serverKey.get

        val request  = sttp.post(uri"http://localhost:9995/login/").auth.basic("andreas", "mysecret")
        val response = request.send()
        val r        = Await.result(response, 3.seconds)

        r.code should be(StatusCodes.Ok)

        val token = Token(r.body.right.get, key).get

        token.permissions.granted.size should be(2)
        token.permissions.granted.find(_.permissionClass == Some("admins")) should be(defined)
        token.permissions.granted.find(_.permissionClass == Some("blended")) should be(defined)
      }
    }

    "Allow a user to login twice" in {
      withLoginService { _ =>
        val key: PublicKey = serverKey.get

        val request = sttp.post(uri"http://localhost:9995/login/").auth.basic("andreas", "mysecret")

        val response1 = request.send()
        val r1        = Await.result(response1, 3.seconds)
        r1.code should be(StatusCodes.Ok)
        val t1 = Token(r1.body.right.get, key).get

        val response2 = request.send()
        val r2        = Await.result(response2, 3.seconds)
        r2.code should be(StatusCodes.Ok)
        val t2 = Token(r2.body.right.get, key).get

        assert(t1.id != t2.id)

      }
    }

    "Respond with the server's public key" in {
      withLoginService { _ =>
        serverKey.get.getFormat() should be("X.509")
        serverKey.get.getAlgorithm() should be("RSA")
      }
    }
  }

  override def cleanUp(): Unit = {
    backend.close()
    Await.result(system.terminate(), 10.seconds)
  }
}
