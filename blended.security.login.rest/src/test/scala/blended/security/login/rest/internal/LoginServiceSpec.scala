package blended.security.login.rest.internal

import java.io.File
import java.security.spec.X509EncodedKeySpec
import java.security.{KeyFactory, PublicKey}

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import blended.akka.http.HttpContext
import blended.akka.http.internal.BlendedAkkaHttpActivator
import blended.akka.internal.BlendedAkkaActivator
import blended.security.internal.SecurityActivator
import blended.security.login.api.Token
import blended.security.login.impl.LoginActivator
import blended.testsupport.pojosr.{PojoSrTestHelper, SimplePojoContainerSpec}
import blended.testsupport.scalatest.LoggingFreeSpecLike
import blended.testsupport.{BlendedTestSupport, RequiresForkedJVM}
import com.softwaremill.sttp._
import com.softwaremill.sttp.akkahttp.AkkaHttpBackend
import org.osgi.framework.BundleActivator
import org.scalatest.Matchers
import sun.misc.BASE64Decoder

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.Try

@RequiresForkedJVM
class LoginServiceSpec extends SimplePojoContainerSpec
  with LoggingFreeSpecLike
  with Matchers
  with PojoSrTestHelper {

  private implicit val timeout : FiniteDuration = 3.seconds

  override def baseDir : String =
    new File(BlendedTestSupport.projectTestOutput, "container").getAbsolutePath()

  override def bundles : Seq[(String, BundleActivator)] = Seq(
    "blended.akka" -> new BlendedAkkaActivator(),
    "blended.akka.http" -> new BlendedAkkaHttpActivator(),
    "blended.security" -> new SecurityActivator(),
    "blended.security.login" -> new LoginActivator(),
    "blended.security.login.rest" -> new RestLoginActivator()
  )

  private def withLoginService[T](request : Request[String, Nothing])(f : Response[String] => T) : T = {
    implicit val system = mandatoryService[ActorSystem](registry)(None)
    implicit val materializer = ActorMaterializer()
    implicit val backend = AkkaHttpBackend()

    mandatoryService[HttpContext](registry)(None)

    val response = request.send()
    f(Await.result(response, 3.seconds))
  }

  private def serverKey() : Try[PublicKey] = Try {

    implicit val system = mandatoryService[ActorSystem](registry)(None)
    implicit val materializer = ActorMaterializer()
    implicit val backend = AkkaHttpBackend()

    val request = sttp.get(uri"http://localhost:9995/login/key")
    val response = request.send()

    val r = Await.result(response, 3.seconds)
    r.code should be(StatusCodes.Ok)

    val rawString = r.body.right.get
      .replace("-----BEGIN PUBLIC KEY-----\n", "")
      .replace("-----END PRIVATE KEY-----", "")
      .replaceAll("\n", "")

    val bytes = new BASE64Decoder().decodeBuffer(rawString)
    val x509 = new X509EncodedKeySpec(bytes)
    val kf = KeyFactory.getInstance("RSA")
    kf.generatePublic(x509)
  }

  "The login service should" - {

    "Respond with Unauthorized used without credentials" in {
      withLoginService(sttp.post(uri"http://localhost:9995/login/")) { r =>
        r.code should be(StatusCodes.Unauthorized)
      }
    }

    "Respond with Unauthorized used with wrong credentials" in {
      withLoginService(
        sttp.post(uri"http://localhost:9995/login/").auth.basic("andreas", "foo")
      ) { r =>
          r.code should be(StatusCodes.Unauthorized)
        }
    }

    "Respond with Ok if called with correct credentials" in {

      val key : PublicKey = serverKey.get

      withLoginService(
        sttp.post(uri"http://localhost:9995/login/").auth.basic("andreas", "mysecret")
      ) { r =>
          r.code should be(StatusCodes.Ok)

          val token = Token(r.body.right.get, key).get

          token.permissions.granted.size should be(2)
          token.permissions.granted.find(_.permissionClass == Some("admins")) should be(defined)
          token.permissions.granted.find(_.permissionClass == Some("blended")) should be(defined)
        }
    }

    "Allow a user to login twice" in {

      val key : PublicKey = serverKey.get
      val request = sttp.post(uri"http://localhost:9995/login/").auth.basic("andreas", "mysecret")

      withLoginService(request) { r1 =>
        r1.code should be(StatusCodes.Ok)
        val t1 = Token(r1.body.right.get, key).get

        withLoginService(request) { r2 =>
          r2.code should be(StatusCodes.Ok)
          val t2 = Token(r2.body.right.get, key).get
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
