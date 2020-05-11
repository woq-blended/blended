package blended.websocket.internal

import java.io.File
import java.security.spec.X509EncodedKeySpec
import java.security.{KeyFactory, PublicKey}
import java.util.Base64

import akka.actor.{ActorRef, ActorSystem}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.ws.{Message, TextMessage, WebSocketRequest, WebSocketUpgradeResponse}
import akka.http.scaladsl.model.{StatusCode, StatusCodes => AkkaStatusCodes}
import akka.stream._
import akka.stream.scaladsl.{Flow, Keep, Sink, Source}
import akka.testkit.TestProbe
import akka.util.ByteString
import akka.{Done, NotUsed}
import blended.akka.http.internal.BlendedAkkaHttpActivator
import blended.akka.internal.BlendedAkkaActivator
import blended.jmx.internal.BlendedJmxActivator
import blended.persistence.h2.internal.H2Activator
import blended.security.internal.SecurityActivator
import blended.security.login.api.Token
import blended.security.login.impl.LoginActivator
import blended.security.login.rest.internal.RestLoginActivator
import blended.testsupport.BlendedTestSupport
import blended.testsupport.pojosr.{PojoSrTestHelper, SimplePojoContainerSpec}
import blended.testsupport.scalatest.LoggingFreeSpecLike
import blended.util.RichTry._
import blended.util.logging.Logger
import blended.websocket._
import blended.websocket.json.PrickleProtocol._
import org.osgi.framework.BundleActivator
import org.scalatest.matchers.should.Matchers
import prickle._
import sttp.client._
import sttp.client.akkahttp.AkkaHttpBackend
import sttp.model.{StatusCode => SC}

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.reflect.ClassTag
import scala.util.Try

abstract class AbstractWebSocketSpec extends SimplePojoContainerSpec
  with LoggingFreeSpecLike
  with Matchers
  with PojoSrTestHelper {

  private val log : Logger = Logger(getClass().getName())
  protected implicit val timeout : FiniteDuration = 3.seconds

  override def baseDir : String = new File(BlendedTestSupport.projectTestOutput, "container").getAbsolutePath()

  override def bundles : Seq[(String, BundleActivator)] = Seq(
    "blended.akka" -> new BlendedAkkaActivator(),
    "blended.jmx" -> new BlendedJmxActivator(),
    "blended.akka.http" -> new BlendedAkkaHttpActivator(),
    "blended.security" -> new SecurityActivator(),
    "blended.security.login" -> new LoginActivator(),
    "blended.security.login.rest" -> new RestLoginActivator(),
    "blended.persistence.h2" -> new H2Activator(),
    "blended.websocket" -> new WebSocketActivator()
  )

  // A convenience method to initialize a web sockets client
  protected def wsFlow(token : String)(implicit system : ActorSystem) : Flow[Message, Message, Future[WebSocketUpgradeResponse]] =
   Http().webSocketClientFlow(WebSocketRequest(s"ws://localhost:9995/ws/?token=$token"))

  // Just a source that stays open, so that actual traffic can happen
  protected val source : Source[TextMessage, ActorRef] = Source.actorRef[TextMessage](1, OverflowStrategy.fail)
  protected val incoming : ActorRef => Sink[Message, NotUsed] = a => Sink.actorRef(a, Done)

  protected implicit val system: ActorSystem = mandatoryService[ActorSystem](registry)(None)
  protected implicit val materializer: Materializer = ActorMaterializer()

  protected def withWebSocketServer[T](
   f : => T
  )(implicit clazz : ClassTag[T]) : T = {
   f
  }

  protected def withWebsocketConnection[T](
    user: String,
    pwd: String,
    probe: ActorRef
  )(f : ActorRef => Token => T) : T = {

    val (switch, actor, token) = wsConnect(user, pwd, probe)
    try {
      f(actor)(token)
    } finally {
      switch.shutdown()
    }
  }

  protected def serverKey()(implicit system : ActorSystem, materializer : Materializer) : Try[PublicKey] = Try {

    implicit val backend = AkkaHttpBackend()

    val request = basicRequest.get(uri"http://localhost:9995/login/key")
    val response = request.send()

    val r = Await.result(response, 3.seconds)
    r.code should be(SC.Ok)

    val rawString = r.body match {
     case Right(k) =>
       k.replace("-----BEGIN PUBLIC KEY-----\n", "")
         .replace("-----END PUBLIC KEY-----", "")
         .replaceAll("\n", "")
     case Left(_) =>
       throw new Exception("Unable to get server public key")
    }

    val bytes = Base64.getDecoder().decode(rawString)
    val x509 = new X509EncodedKeySpec(bytes)
    val kf = KeyFactory.getInstance("RSA")
    kf.generatePublic(x509)
  }

  protected def login(user : String, password : String)(implicit system : ActorSystem, materializer : Materializer) : Try[Token] = {

   implicit val backend = AkkaHttpBackend()

   val key : PublicKey = serverKey().unwrap

   val request = basicRequest.post(uri"http://localhost:9995/login/").auth.basic(user, password)
   val response = request.send()
   val r = Await.result(response, 3.seconds)

   r.code should be(SC.Ok)
   Token(r.body.right.get, key)
  }

  def wsConnect(user : String, password : String, wsListener : ActorRef)(
   implicit system : ActorSystem, materializer : Materializer
  ) : (KillSwitch, ActorRef, Token) = {
   val token : Token = login(user, password).unwrap

   // We need to set up a kill switch, so that the client can be closed
   val ((actor, resp), switch) = source
     .viaMat(wsFlow(token.webToken))(Keep.both)
     .viaMat(KillSwitches.single)(Keep.both)
     .toMat(incoming(wsListener))(Keep.left)
     .run()

   // Make sure we are connected
   val connected = Await.result(resp, 3.seconds)
   connected.response.status should be(AkkaStatusCodes.SwitchingProtocols)

   (switch, actor, token)
  }

  def fishForWsUpdate[T](t : FiniteDuration)(
    probe : TestProbe,
    status : StatusCode = AkkaStatusCodes.OK
  )(f : T => Boolean)(implicit up : Unpickler[T]) : Any = {
    val msg : TextMessage = probe.expectMsgType[TextMessage](t)

    val s : String = msg match {
      case strict : TextMessage.Strict => strict.getStrictText
      case streamed : TextMessage.Streamed =>
        Await.result(streamed.textStream.runWith(Sink.seq[String]), 3.seconds).head
    }

    log.debug(s"Received WebSocket message : [${s}]")
    val enc: WsMessageEncoded = Unpickle[WsMessageEncoded].fromString(s).unwrap
    assert(enc.context.status == status.intValue())
    val obj : T = enc.decode[T].unwrap
    log.debug(s"Got wrapped object [$obj]")
    assert(f(obj))
  }
}
