package blended.akka.http.restjms.internal

import java.net.URI

import akka.stream.scaladsl.Source
import akka.util.ByteString
import blended.testsupport.RequiresForkedJVM
import sttp.client.akkahttp.AkkaHttpBackend
import sttp.client._
import sttp.model.{Uri, StatusCode}

import scala.concurrent.Await
import scala.concurrent.duration._

@RequiresForkedJVM
class JMSChunkedRequestorSpec extends AbstractJmsRequestorSpec {

  "The Jms Requestor should " - {

    "behave correctly with streamed or chunked bodies" in {

      implicit val backend = AkkaHttpBackend.usingActorSystem(actorSystem)

      val uri = Uri(new URI(s"${plainServerUrl(registry)}/restjms/leergut.redeem"))
      val request = basicRequest
        .streamBody(Source.single(ByteString("test")))
        //.body("test")
        .contentType("application/json")
        .post(uri)

      val response = Await.result(request.send(), 3.seconds)

      assert(response.code == StatusCode.Ok)
    }

    "behave correctly with non-streamed bodies" in {

      implicit val backend = AkkaHttpBackend.usingActorSystem(actorSystem)

      val uri = Uri(new URI(s"${plainServerUrl(registry)}/restjms/leergut.redeem"))
      val request = basicRequest
        .body("test")
        .contentType("application/json")
        .post(uri)

      val response = Await.result(request.send(), 3.seconds)

      assert(response.code == StatusCode.Ok)
    }
  }
}
