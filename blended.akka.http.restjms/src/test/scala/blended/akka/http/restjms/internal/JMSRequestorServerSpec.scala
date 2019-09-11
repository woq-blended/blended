package blended.akka.http.restjms.internal

import java.net.URI

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.Http.ServerBinding
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Source
import akka.testkit.TestKit
import akka.util.ByteString
import com.softwaremill.sttp._
import com.softwaremill.sttp.akkahttp.AkkaHttpBackend
import org.scalatest.{BeforeAndAfterAll, FreeSpecLike}

import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration._

class JMSRequestorServerSpec extends TestKit(ActorSystem("jmsRequestor"))
  with FreeSpecLike
  with BeforeAndAfterAll
  with JMSRequestorSetup {

  implicit val actorSystem : ActorSystem = system
  implicit val materializer : ActorMaterializer = ActorMaterializer()
  implicit val backend : SttpBackend[Future, Source[ByteString, Any]] = AkkaHttpBackend.usingActorSystem(system)
  implicit val executionContext : ExecutionContext = system.dispatcher

  private[this] var svrBinding : Option[ServerBinding] = None
  private[this] val port = 9999

  val svc = new SimpleRestJmsService(restJmsConfig.operations, materializer, system.dispatcher)

  override protected def beforeAll() : Unit = {
    val binding = Http().bindAndHandle(svc.httpRoute, "localhost", port)
    svrBinding = Some(Await.result(binding, 10.seconds))
  }

  override protected def afterAll() : Unit = svrBinding.foreach(_.unbind().flatMap(_ => system.terminate()))

  "The Jms Requestor should" - {

    "behave correctly with streamed or chunked bodies" in {

      val uri = Uri(new URI(s"http://localhost:$port/leergut.redeem"))
      val request = sttp
        .streamBody(Source.single(ByteString("test")))
        //.body("test")
        .contentType("application/json")
        .post(uri)

      val response = Await.result(request.send(), 3.seconds)

      assert(response.code == StatusCodes.Ok)
    }

    "behave correctly with non-streamed bodies" in {

      val uri = Uri(new URI(s"http://localhost:$port/leergut.redeem"))
      val request = sttp
        .body("test")
        .contentType("application/json")
        .post(uri)

      val response = Await.result(request.send(), 3.seconds)

      assert(response.code == StatusCodes.Ok)
    }
  }
}
