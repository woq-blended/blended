package blended.mgmt.ws.internal

import java.io.File
import java.security.spec.X509EncodedKeySpec
import java.security.{KeyFactory, PublicKey}
import java.util.concurrent.{CompletionStage, TimeUnit}

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
import blended.mgmt.rest.internal.MgmtRestActivator
import blended.persistence.h2.internal.H2Activator
import blended.security.internal.SecurityActivator
import blended.security.login.api.Token
import blended.security.login.impl.LoginActivator
import blended.security.login.rest.internal.RestLoginActivator
import blended.testsupport.BlendedTestSupport
import blended.testsupport.pojosr.{BlendedPojoRegistry, PojoSrTestHelper, SimplePojoContainerSpec}
import blended.testsupport.scalatest.LoggingFreeSpecLike
import blended.updater.config.ContainerInfo
import blended.updater.config.json.PrickleProtocol._
import blended.updater.remote.internal.RemoteUpdaterActivator
import com.softwaremill.sttp.{SttpBackend, _}
import com.softwaremill.sttp.akkahttp.AkkaHttpBackend
import org.osgi.framework.BundleActivator
import org.scalatest.Matchers
import prickle.{Pickle, Unpickle}
import sun.misc.BASE64Decoder

import scala.collection.JavaConverters._
import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import scala.reflect.ClassTag
import scala.util.Try

class MgmtWebSocketSpec extends SimplePojoContainerSpec
  with LoggingFreeSpecLike
  with Matchers
  with PojoSrTestHelper {

  private implicit val timeout : FiniteDuration = 3.seconds

  override def baseDir: String = new File(BlendedTestSupport.projectTestOutput, "container").getAbsolutePath()

  override def bundles: Seq[(String, BundleActivator)] = Seq(
    "blended.akka" -> new BlendedAkkaActivator(),
    "blended.akka.http" -> new BlendedAkkaHttpActivator(),
    "blended.security" -> new SecurityActivator(),
    "blended.security.login" -> new LoginActivator(),
    "blended.security.login.rest" -> new RestLoginActivator(),
    "blended.persistence.h2" -> new H2Activator(),
    "blended.updater.remote" -> new RemoteUpdaterActivator(),
    "blended.mgmt.rest" -> new MgmtRestActivator(),
    "blended.mgmt.ws" -> new MgmtWSActivator()
  )

  // A convenience method to initialize a web sockets client
  private[this] def wsFlow(token: String)(implicit system: ActorSystem) =
    Http().webSocketClientFlow(WebSocketRequest(s"ws://localhost:9995/mgmtws/?token=$token"))

  // Just a source that stays open, so that actual traffic can happen
  private[this] val source = Source.actorRef[TextMessage](1, OverflowStrategy.fail)

  // We collect the stream of incoming Container Info's in a sequence
  private[this] val incoming : Sink[Message, CompletionStage[java.util.List[Message]]] = Sink.seq[Message]

  private[this] def withWebSocketServer[T](sr : BlendedPojoRegistry)(f : ActorSystem => Materializer => T)(implicit clazz : ClassTag[T]) : T = {
    val system = mandatoryService[ActorSystem](sr)(None)
    val materializer = ActorMaterializer()(system)
    f(system)(materializer)
  }

  private[this] def serverKey()(implicit system: ActorSystem, materializer: Materializer) : Try[PublicKey] = Try {

    implicit val backend : SttpBackend[Future, Source[ByteString, Any]] = AkkaHttpBackend()

    val request = sttp.get(uri"http://localhost:9995/login/key")
    val response = request.send()

    val r = Await.result(response, 3.seconds)
    r.code should be (StatusCodes.Ok)

    val rawString = r.body.right.get
      .replace("-----BEGIN PUBLIC KEY-----\n", "")
      .replace("-----END PUBLIC KEY-----", "")
      .replaceAll("\n", "")

    val bytes = new BASE64Decoder().decodeBuffer(rawString)
    val x509 = new X509EncodedKeySpec(bytes)
    val kf = KeyFactory.getInstance("RSA")
    kf.generatePublic(x509)
  }

  private[this] def login(user: String, password: String)(implicit system: ActorSystem, materializer: Materializer) : Try[Token] = {

    implicit val backend : SttpBackend[Future, Source[ByteString, Any]] = AkkaHttpBackend()

    val key : PublicKey = serverKey().get

    val request = sttp.post(uri"http://localhost:9995/login/").auth.basic(user, password)
    val response = request.send()
    val r = Await.result(response, 3.seconds)

    r.code should be (StatusCodes.Ok)
    Token(r.body.right.get, key)
  }

  private[this] def ctInfo(id: String, country: String) : ContainerInfo = ContainerInfo(
    containerId = id,
    properties = Map("country" -> country),
    List.empty,
    List.empty,
    System.currentTimeMillis(),
    List.empty
  )

  private[this] val containers : Seq[ContainerInfo] = {
    val countries = List("de", "bg")

    for {
      i <- 1.to(10)
      c <- countries
    } yield ctInfo("id" + i, c)
  }

  private def countryContainer(country: String) : Seq[ContainerInfo] =
    containers.filter(_.properties.get("country").contains(country))

  private[this] def postContainer()(implicit system: ActorSystem, materializer: Materializer) : Try[Unit] = Try {

    implicit val backend : SttpBackend[Future, Source[ByteString, Any]] = AkkaHttpBackend()

    val ctPost = sttp.post(uri"http://localhost:9995/mgmt/container")
      .contentType("application/json")

    containers.foreach { ct =>
      val ctResp = Await.result(ctPost.body(Pickle.intoString(ct)).send(), 3.seconds)
      ctResp.code should be(StatusCodes.Ok)
    }
  }

  private[this] def resultContainer(l : java.util.List[Message]) : Try[List[ContainerInfo]] = Try {
    l.asScala.collect {
      case tm : TextMessage.Strict => Unpickle[ContainerInfo].fromString(tm.text).get
    }.toList
  }

  def wsConnect(user: String, password: String)(implicit system : ActorSystem, materializer: Materializer) : (KillSwitch, CompletionStage[java.util.List[Message]]) = {
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
      withWebSocketServer(registry) { actorSystem => actorMaterializer =>
        implicit val system : ActorSystem = actorSystem
        implicit val materializer : Materializer = actorMaterializer

        val flow = Http().webSocketClientFlow(WebSocketRequest("ws://localhost:9995/mgmtws/"))

        val (resp, _) = source
          .viaMat(flow)(Keep.right)
          .toMat(incoming)(Keep.both)
          .run()

        // if no token is given in the parameters, a 404 is returned
        val connected = Await.result(resp, 3.seconds)
        connected.response.status should be (AkkaStatusCodes.NotFound)
      }
    }

    "reject clients with a fantasy token" in {
      withWebSocketServer(registry) { actorSystem => actorMaterializer =>
        implicit val system : ActorSystem = actorSystem
        implicit val materializer : Materializer = actorMaterializer

        val flow = Http().webSocketClientFlow(WebSocketRequest("ws://localhost:9995/mgmtws/?token=foo"))

        val (resp, _) = source
          .viaMat(flow)(Keep.right)
          .toMat(incoming)(Keep.both)
          .run()

        val connected = Await.result(resp, 3.seconds)
        connected.response.status should be (AkkaStatusCodes.Unauthorized)
      }
    }

    "accept clients with a real token" in {

      withWebSocketServer(registry) { actorSystem => actorMaterializer =>
        implicit val system : ActorSystem = actorSystem
        implicit val materializer : Materializer = actorMaterializer

        // login and retrieve the token
        val token = login("bg_test", "secret").get

        // set up the WS listener
        val (resp, _) = source
          .viaMat(wsFlow(token.webToken))(Keep.right)
          .toMat(incoming)(Keep.both)
          .run()

        // We are expecting a Switch Protocol result when the WS client is connected
        val connected = Await.result(resp, 3.seconds)
        connected.response.status should be (AkkaStatusCodes.SwitchingProtocols)
      }
    }

    "receive updates only for granted objects" in {

      withWebSocketServer(registry) { actorSystem => actorMaterializer =>
        implicit val system : ActorSystem = actorSystem
        implicit val materializer : Materializer = actorMaterializer

        val (switch, messages) = wsConnect("root", "mysecret")
        val (deSwitch, deMessages) = wsConnect("de_test", "secret")
        val (bgSwitch, bgMessages) = wsConnect("bg_test", "secret")

        // post the mock container Infos
        postContainer()

        // Close the WS client listener
        switch.shutdown()
        deSwitch.shutdown()
        bgSwitch.shutdown()

        // examine the received messages
        val results = resultContainer(messages.toCompletableFuture.get(3, TimeUnit.SECONDS)).get
        val deResults = resultContainer(deMessages.toCompletableFuture.get(3, TimeUnit.SECONDS)).get
        val bgResults = resultContainer(bgMessages.toCompletableFuture.get(3, TimeUnit.SECONDS)).get

        assert(results.size == containers.size)
        assert(deResults.size == countryContainer("de").size)
        assert(deResults.size == countryContainer("bg").size)

        assert(deResults.forall(_.properties.get("country").contains("de")))
        assert(bgResults.forall(_.properties.get("country").contains("bg")))
      }
    }
  }

}
