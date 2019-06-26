package blended.websocket.internal

import java.io.File
import java.security.spec.X509EncodedKeySpec
import java.security.{KeyFactory, PublicKey}
import java.util.Base64

import akka.actor.{ActorRef, ActorSystem}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.ws.{Message, TextMessage, WebSocketRequest}
import akka.http.scaladsl.model.{StatusCode, StatusCodes => AkkaStatusCodes}
import akka.stream._
import akka.stream.javadsl.Sink
import akka.stream.scaladsl.{Keep, Source}
import akka.testkit.TestProbe
import akka.util.ByteString
import akka.{Done, NotUsed}
import blended.akka.http.internal.BlendedAkkaHttpActivator
import blended.akka.internal.BlendedAkkaActivator
import blended.persistence.h2.internal.H2Activator
import blended.security.internal.SecurityActivator
import blended.security.login.api.Token
import blended.security.login.impl.LoginActivator
import blended.security.login.rest.internal.RestLoginActivator
import blended.testsupport.BlendedTestSupport
import blended.testsupport.pojosr.{BlendedPojoRegistry, PojoSrTestHelper, SimplePojoContainerSpec}
import blended.testsupport.scalatest.LoggingFreeSpecLike
import blended.websocket.{BlendedWsMessages, Version, VersionResponse, WsContext, WsMessageEncoded}
import com.softwaremill.sttp.akkahttp.AkkaHttpBackend
import com.softwaremill.sttp._
import org.osgi.framework.BundleActivator
import org.scalatest.Matchers
import prickle._
import blended.websocket.json.PrickleProtocol._

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.reflect.ClassTag
import scala.util.Try

class WebSocketSpec extends SimplePojoContainerSpec
  with LoggingFreeSpecLike
  with Matchers
  with PojoSrTestHelper {

  private implicit val timeout : FiniteDuration = 3.seconds

  override def baseDir : String = new File(BlendedTestSupport.projectTestOutput, "container").getAbsolutePath()

  override def bundles : Seq[(String, BundleActivator)] = Seq(
   "blended.akka" -> new BlendedAkkaActivator(),
   "blended.akka.http" -> new BlendedAkkaHttpActivator(),
   "blended.security" -> new SecurityActivator(),
   "blended.security.login" -> new LoginActivator(),
   "blended.security.login.rest" -> new RestLoginActivator(),
   "blended.persistence.h2" -> new H2Activator(),
   "blended.websocket" -> new WebSocketActivator()
  )

  // A convenience method to initialize a web sockets client
  private[this] def wsFlow(token : String)(implicit system : ActorSystem) =
   Http().webSocketClientFlow(WebSocketRequest(s"ws://localhost:9995/ws/?token=$token"))

  // Just a source that stays open, so that actual traffic can happen
  private[this] val source = Source.actorRef[TextMessage](1, OverflowStrategy.fail)
  private[this] val incoming : ActorRef => Sink[Message, NotUsed] = a => Sink.actorRef(a, Done)

  private[this] def withWebSocketServer[T](sr : BlendedPojoRegistry)(f : ActorSystem => Materializer => T)(implicit clazz : ClassTag[T]) : T = {
   val system = mandatoryService[ActorSystem](sr)(None)
   val materializer = ActorMaterializer()(system)
   f(system)(materializer)
  }

  private[this] def serverKey()(implicit system : ActorSystem, materializer : Materializer) : Try[PublicKey] = Try {

   implicit val backend : SttpBackend[Future, Source[ByteString, Any]] = AkkaHttpBackend()

   val request = sttp.get(uri"http://localhost:9995/login/key")
   val response = request.send()

   val r = Await.result(response, 3.seconds)
   r.code should be(StatusCodes.Ok)

   val rawString = r.body.right.get
     .replace("-----BEGIN PUBLIC KEY-----\n", "")
     .replace("-----END PUBLIC KEY-----", "")
     .replaceAll("\n", "")

   val bytes = Base64.getDecoder().decode(rawString)
   val x509 = new X509EncodedKeySpec(bytes)
   val kf = KeyFactory.getInstance("RSA")
   kf.generatePublic(x509)
  }

  private[this] def login(user : String, password : String)(implicit system : ActorSystem, materializer : Materializer) : Try[Token] = {

   implicit val backend : SttpBackend[Future, Source[ByteString, Any]] = AkkaHttpBackend()

   val key : PublicKey = serverKey().get

   val request = sttp.post(uri"http://localhost:9995/login/").auth.basic(user, password)
   val response = request.send()
   val r = Await.result(response, 3.seconds)

   r.code should be(StatusCodes.Ok)
   Token(r.body.right.get, key)
  }

  def wsConnect(user : String, password : String, wsListener : ActorRef)(
   implicit system : ActorSystem, materializer : Materializer
  ) : (KillSwitch, ActorRef) = {
   val token = login(user, password).get

   // We need to set up a kill switch, so that the client can be closed
   val ((actor, resp), switch) = source
     .viaMat(wsFlow(token.webToken))(Keep.both)
     .viaMat(KillSwitches.single)(Keep.both)
     .toMat(incoming(wsListener))(Keep.left)
     .run()

   // Make sure we are connected
   val connected = Await.result(resp, 3.seconds)
   connected.response.status should be(AkkaStatusCodes.SwitchingProtocols)

   (switch, actor)
  }

  "The Web socket server should" - {

   "reject clients without token" in {
     withWebSocketServer(registry) { actorSystem =>
       actorMaterializer =>
         implicit val system: ActorSystem = actorSystem
         implicit val materializer: Materializer = actorMaterializer

         val flow = Http().webSocketClientFlow(WebSocketRequest("ws://localhost:9995/ws/"))

         val (resp, _) = source
           .viaMat(flow)(Keep.right)
           .toMat(incoming(TestProbe().ref))(Keep.both)
           .run()

         // if no token is given in the parameters, a 404 is returned
         val connected = Await.result(resp, 3.seconds)
         connected.response.status should be(AkkaStatusCodes.NotFound)
     }
   }

   "reject clients with a fantasy token" in {
     withWebSocketServer(registry) { actorSystem =>
       actorMaterializer =>
         implicit val system: ActorSystem = actorSystem
         implicit val materializer: Materializer = actorMaterializer

         val flow = Http().webSocketClientFlow(WebSocketRequest("ws://localhost:9995/ws/?token=foo"))

         val (resp, _) = source
           .viaMat(flow)(Keep.right)
           .toMat(incoming(TestProbe().ref))(Keep.both)
           .run()

         val connected = Await.result(resp, 3.seconds)
         connected.response.status should be(AkkaStatusCodes.Unauthorized)
     }
   }

   "accept clients with a real token" in {

     withWebSocketServer(registry) { actorSystem =>
       actorMaterializer =>
         implicit val system: ActorSystem = actorSystem
         implicit val materializer: Materializer = actorMaterializer

         // login and retrieve the token
         val token = login("bg_test", "secret").get

         // set up the WS listener
         val ((_, resp), _) = source
           .viaMat(wsFlow(token.webToken))(Keep.both)
           .toMat(incoming(TestProbe().ref))(Keep.both)
           .run()

         // We are expecting a Switch Protocol result when the WS client is connected
         val connected = Await.result(resp, 3.seconds)
         connected.response.status should be(AkkaStatusCodes.SwitchingProtocols)
     }
   }

   "respond to a malformed web socket request with BAD_REQUEST" in {
     withWebSocketServer(registry) { actorSystem =>
       actorMaterializer =>
         implicit val system: ActorSystem = actorSystem
         implicit val materializer: Materializer = actorMaterializer

         val probe: TestProbe = TestProbe()
         // login and retrieve the token
         val (switch, actor) = wsConnect("bg_test", "secret", probe.ref)

         actor ! TextMessage.Strict("Hello Blended")

         // As the text is not a properly encoded command, it will return a BadRequest on
         // the websockets stream
         probe.fishForMessage(3.seconds) {
           case m: TextMessage.Strict =>
             val enc: WsMessageEncoded = Unpickle[WsMessageEncoded].fromString(m.getStrictText).get
             enc.context.status == AkkaStatusCodes.BadRequest.intValue
         }

         switch.shutdown()
     }
   }

   "respond to an unknown command with a NOT_FOUND" in {

     def fishForResponse : StatusCode => TestProbe => Try[Unit] = sc => p => Try {
       p.fishForMessage(3.seconds) {
         case m: TextMessage.Strict =>
           val enc: WsMessageEncoded = Unpickle[WsMessageEncoded].fromString(m.getStrictText).get
           enc.context.status == sc.intValue
       }
     }

     withWebSocketServer(registry) { actorSystem =>
       actorMaterializer =>
         implicit val system: ActorSystem = actorSystem
         implicit val materializer: Materializer = actorMaterializer

         val probe: TestProbe = TestProbe()
         // login and retrieve the token
         val (switch, actor) = wsConnect("bg_test", "secret", probe.ref)

         actor ! TextMessage.Strict(WsMessageEncoded.fromContext(WsContext(namespace = "foo", name = "bar")))
         fishForResponse(AkkaStatusCodes.NotFound)(probe).get

         actor ! TextMessage.Strict(WsMessageEncoded.fromContext(WsContext(namespace = "blended", name = "doesNotExist")))
         fishForResponse(AkkaStatusCodes.NotFound)(probe).get

         switch.shutdown()
     }
    }

    "respond to a valid Web Socket request" in {
      withWebSocketServer(registry) { actorSystem =>
        actorMaterializer =>
          implicit val system: ActorSystem = actorSystem
          implicit val materializer: Materializer = actorMaterializer

          val probe: TestProbe = TestProbe()
          // login and retrieve the token
          val (switch, actor) = wsConnect("bg_test", "secret", probe.ref)

          val blendedCmd : BlendedWsMessages = Version()

          val msg = WsMessageEncoded.fromObject(WsContext(namespace = "blended", name = "version"), blendedCmd)
          actor ! TextMessage.Strict(msg)

          // As the text is not a properly encoded command, it will return a BadRequest on
          // the websockets stream
          probe.fishForMessage(3.seconds) {
            case m: TextMessage.Strict =>
              val enc: WsMessageEncoded = Unpickle[WsMessageEncoded].fromString(m.getStrictText).get
              enc.context.status == AkkaStatusCodes.OK.intValue
          }

          switch.shutdown()
      }
    }

  }
}
