package blended.mgmt.ws.internal

import java.io.File
import java.security.{KeyFactory, PublicKey}
import java.security.spec.X509EncodedKeySpec
import java.util.concurrent.CompletionStage

import akka.Done
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{StatusCodes => AkkaStatusCodes}
import akka.http.scaladsl.model.ws.{Message, TextMessage, WebSocketRequest}
import akka.stream.ActorMaterializer
import akka.stream.javadsl.Sink
import akka.stream.scaladsl.{Keep, Source}
import akka.testkit.TestKit
import blended.akka.http.internal.BlendedAkkaHttpActivator
import blended.akka.internal.BlendedAkkaActivator
import blended.mgmt.rest.internal.MgmtRestActivator
import blended.persistence.h2.internal.H2Activator
import blended.security.internal.SecurityActivator
import blended.security.login.api.Token
import blended.security.login.impl.LoginActivator
import blended.security.login.rest.internal.RestLoginActivator
import blended.testsupport.BlendedTestSupport
import blended.testsupport.pojosr.{PojoSrTestHelper, SimplePojosrBlendedContainer}
import org.scalatest.{FreeSpecLike, Matchers}
import sun.misc.BASE64Decoder

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.Try
import com.softwaremill.sttp._
import com.softwaremill.sttp.akkahttp.AkkaHttpBackend


class MgmtWebSocketSpec extends TestKit(ActorSystem("test"))
  with FreeSpecLike
  with Matchers
  with SimplePojosrBlendedContainer
  with PojoSrTestHelper {

  implicit val actorSystem = system
  implicit val actorMaterializer = ActorMaterializer()
  implicit val eCtxt = system.dispatcher
  implicit val backend = AkkaHttpBackend()

  private[this] val source = Source.empty[TextMessage]

  private[this] val incoming : Sink[Message, CompletionStage[Done]]  = Sink.foreach[Message] {
    case message: TextMessage.Strict => println(message)
  }

  private[this] def withWebSocketServer[T](f : () => T) : T = {
    withSimpleBlendedContainer(new File(BlendedTestSupport.projectTestOutput, "container").getAbsolutePath()){sr =>
      withStartedBundles(sr)(Seq(
        "blended.akka" -> Some(() => new BlendedAkkaActivator()),
        "blended.akka.http" -> Some(() => new BlendedAkkaHttpActivator()),
        "blended.security" -> Some(() => new SecurityActivator()),
        "blended.security.login" -> Some(() => new LoginActivator()),
        "blended.security.login.rest" -> Some(() => new RestLoginActivator()),
        "blended.persistence.h2" -> Some(() => new H2Activator()),
        "blended.mgmt.rest" -> Some(() => new MgmtRestActivator()),
        "blended.mgmt.ws" -> Some(() => new MgmtWSActivator())
      )) { sr =>
        f()
      }
    }
  }

  def serverKey : Try[PublicKey] = Try {

    val request = sttp.get(uri"http://localhost:9995/login/key")
    val response = request.send()

    val r = Await.result(response, 3.seconds)
    r.code should be (StatusCodes.Ok)

    val rawString = r.body.right.get
      .replace("-----BEGIN PUBLIC KEY-----\n", "")
      .replace("-----END PRIVATE KEY-----", "")
      .replaceAll("\n", "")

    val bytes = new BASE64Decoder().decodeBuffer(rawString)
    val x509 = new X509EncodedKeySpec(bytes)
    val kf = KeyFactory.getInstance("RSA")
    kf.generatePublic(x509)
  }

  "The Web socket server should" - {

    "reject clients without token" in {
      withWebSocketServer { () =>
        val flow = Http().webSocketClientFlow(WebSocketRequest("ws://localhost:9995/mgmtws/"))

        val (resp, closed) = source
          .viaMat(flow)(Keep.right)
          .toMat(incoming)(Keep.both)
          .run()

        // if no token is given in the parameters, a 404 is returned
        val connected = Await.result(resp, 3.seconds)
        connected.response.status should be (AkkaStatusCodes.NotFound)
      }
    }

    "reject clients with a fantasy token" in {
      withWebSocketServer { () =>
        val flow = Http().webSocketClientFlow(WebSocketRequest("ws://localhost:9995/mgmtws/?token=foo"))

        val (resp, closed) = source
          .viaMat(flow)(Keep.right)
          .toMat(incoming)(Keep.both)
          .run()

        val connected = Await.result(resp, 3.seconds)
        connected.response.status should be (AkkaStatusCodes.Unauthorized)
      }
    }

    "accept clients with a real token" in {


      withWebSocketServer { () =>

        val key : PublicKey = serverKey.get

        val request = sttp.post(uri"http://localhost:9995/login/").auth.basic("tester", "mysecret")
        val response = request.send()
        val r = Await.result(response, 3.seconds)

        r.code should be (StatusCodes.Ok)
        val token = Token(r.body.right.get, key).get

        val flow = Http().webSocketClientFlow(WebSocketRequest(s"ws://localhost:9995/mgmtws/?token=${token.webToken}"))

        val (resp, closed) = source
          .viaMat(flow)(Keep.right)
          .toMat(incoming)(Keep.both)
          .run()

        val connected = Await.result(resp, 3.seconds)
        connected.response.status should be (AkkaStatusCodes.SwitchingProtocols)
      }
    }
  }

  "receive updates only for granted objects" in {
    pending
  }

}
