package blended.websocket.internal

import java.io.File
import java.security.spec.X509EncodedKeySpec
import java.security.{KeyFactory, PublicKey}
import java.util.Base64
import java.util.concurrent.CompletionStage

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.ws.{Message, TextMessage, WebSocketRequest}
import akka.http.scaladsl.model.{StatusCodes => AkkaStatusCodes}
import akka.stream._
import akka.stream.javadsl.Sink
import akka.stream.scaladsl.{Keep, Source}
import akka.util.ByteString
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
import com.softwaremill.sttp.akkahttp.AkkaHttpBackend
import com.softwaremill.sttp.{SttpBackend, _}
import org.osgi.framework.BundleActivator
import org.scalatest.Matchers

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

   // We collect the stream of incoming Container Info's in a sequence
   private[this] val incoming : Sink[Message, CompletionStage[java.util.List[Message]]] = Sink.seq[Message]

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

   def wsConnect(user : String, password : String)(
     implicit system : ActorSystem, materializer : Materializer
   ) : (KillSwitch, CompletionStage[java.util.List[Message]]) = {
     val token = login(user, password).get

     // We need to set up a kill switch, so that the client can be closed
     val ((resp, switch), messages) = source
       .viaMat(wsFlow(token.webToken))(Keep.right)
       .viaMat(KillSwitches.single)(Keep.both)
       .toMat(incoming)(Keep.both)
       .run()

     // Make sure we are connected
     val connected = Await.result(resp, 3.seconds)
     connected.response.status should be(AkkaStatusCodes.SwitchingProtocols)

     (switch, messages)
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
             .toMat(incoming)(Keep.both)
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
             .toMat(incoming)(Keep.both)
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
           val (resp, _) = source
             .viaMat(wsFlow(token.webToken))(Keep.right)
             .toMat(incoming)(Keep.both)
             .run()

           // We are expecting a Switch Protocol result when the WS client is connected
           val connected = Await.result(resp, 3.seconds)
           connected.response.status should be(AkkaStatusCodes.SwitchingProtocols)
       }
     }
   }
 }
