package blended.security.login.rest.internal

import java.io.File
import java.security.spec.X509EncodedKeySpec
import java.security.{KeyFactory, PublicKey}
import java.util.Base64

import blended.akka.http.HttpContext
import blended.akka.http.internal.BlendedAkkaHttpActivator
import blended.akka.internal.BlendedAkkaActivator
import blended.jmx.internal.BlendedJmxActivator
import blended.security.internal.SecurityActivator
import blended.security.login.api.Token
import blended.security.login.impl.LoginActivator
import blended.testsupport.pojosr.{AkkaHttpServerTestHelper, PojoSrTestHelper, SimplePojoContainerSpec}
import blended.testsupport.scalatest.LoggingFreeSpecLike
import blended.testsupport.{BlendedTestSupport, RequiresForkedJVM}
import org.osgi.framework.BundleActivator
import org.scalatest.matchers.should.Matchers
import sttp.client._
import sttp.client.akkahttp.AkkaHttpBackend
import sttp.model.StatusCode

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.Try

@RequiresForkedJVM
class LoginServiceSpec extends SimplePojoContainerSpec
  with LoggingFreeSpecLike
  with Matchers
  with PojoSrTestHelper
  with AkkaHttpServerTestHelper {

  override def baseDir : String =
    new File(BlendedTestSupport.projectTestOutput, "container").getAbsolutePath()

  override def bundles : Seq[(String, BundleActivator)] = Seq(
    "blended.jmx" -> new BlendedJmxActivator(),
    "blended.akka" -> new BlendedAkkaActivator(),
    "blended.akka.http" -> new BlendedAkkaHttpActivator(),
    "blended.security" -> new SecurityActivator(),
    "blended.security.login" -> new LoginActivator(),
    "blended.security.login.rest" -> new RestLoginActivator()
  )

  private def withLoginService[T](request : Request[Either[String, String], Nothing])(f : Response[Either[String, String]] => T) : T = {
    implicit val to : FiniteDuration = timeout

    implicit val backend = AkkaHttpBackend()
    mandatoryService[HttpContext](registry, None)

    val response = request.send()
    f(Await.result(response, timeout))
  }

  private def serverKey() : Try[PublicKey] = Try {

    implicit val backend = AkkaHttpBackend()

    val request = basicRequest.get(uri"${plainServerUrl(registry)}/login/key")
    val response = request.send()

    val r = Await.result(response, 3.seconds)
    r.code should be(StatusCode.Ok)

    val rawString = r.body.getOrElse(throw new NoSuchElementException("right"))
      .replace("-----BEGIN PUBLIC KEY-----\n", "")
      .replace("-----END PUBLIC KEY-----", "")
      .replaceAll("\n", "")

    val bytes : Array[Byte] = Base64.getDecoder().decode(rawString)
    val x509 = new X509EncodedKeySpec(bytes)
    val kf = KeyFactory.getInstance("RSA")
    kf.generatePublic(x509)
  }

  "The login service should" - {

    "Respond with Unauthorized used without credentials" in {
      withLoginService(basicRequest.post(uri"${plainServerUrl(registry)}/login/")) { r =>
        r.code should be(StatusCode.Unauthorized)
      }
    }

    "Respond with Unauthorized used with wrong credentials" in {
      withLoginService(
        basicRequest.post(uri"${plainServerUrl(registry)}/login/").auth.basic("andreas", "foo")
      ) { r =>
          r.code should be(StatusCode.Unauthorized)
        }
    }

    "Respond with Ok if called with correct credentials" in {

      val key : PublicKey = serverKey().get

      withLoginService(
        basicRequest.post(uri"${plainServerUrl(registry)}/login/").auth.basic("andreas", "mysecret")
      ) { r =>
          r.code should be(StatusCode.Ok)

          val token = Token(r.body.getOrElse(throw new NoSuchElementException("right")), key).get

          token.permissions.granted.size should be(2)
          token.permissions.granted.find(_.permissionClass.contains("admins")) should be(defined)
          token.permissions.granted.find(_.permissionClass.contains("blended")) should be(defined)
        }
    }

    "Allow a user to login twice" in {

      val key : PublicKey = serverKey().get
      val request = basicRequest.post(uri"${plainServerUrl(registry)}/login/").auth.basic("andreas", "mysecret")

      withLoginService(request) { r1 =>
        r1.code should be(StatusCode.Ok)
        val t1 = Token(r1.body.getOrElse(throw new NoSuchElementException("right")), key).get

        withLoginService(request) { r2 =>
          r2.code should be(StatusCode.Ok)
          val t2 = Token(r2.body.getOrElse(throw new NoSuchElementException("right")), key).get
          assert(t1.id != t2.id)
        }
      }
    }

    "Respond with the server's public key" in {
      val key = serverKey().get

      key.getFormat() should be("X.509")
      key.getAlgorithm() should be("RSA")
    }
  }
}
