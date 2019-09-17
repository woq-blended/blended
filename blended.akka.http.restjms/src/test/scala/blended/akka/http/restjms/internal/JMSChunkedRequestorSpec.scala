package blended.akka.http.restjms.internal

import java.net.URI

import akka.stream.scaladsl.Source
import akka.util.ByteString
import com.softwaremill.sttp._
import com.softwaremill.sttp.akkahttp.AkkaHttpBackend

import scala.concurrent.Await
import scala.concurrent.duration._

class JMSChunkedRequestorSpec extends AbstractJmsRequestorSpec {

  implicit val backend = AkkaHttpBackend.usingActorSystem(system)

  "The Jms Requestor should " - {

    "behave correctly with streamed or chunked bodies" in {

      val uri = Uri(new URI(s"$svcUrlBase/leergut.redeem"))
      val request = sttp
        .streamBody(Source.single(ByteString("test")))
        //.body("test")
        .contentType("application/json")
        .post(uri)

      val response = Await.result(request.send(), 3.seconds)

      assert(response.code == StatusCodes.Ok)
    }

    "behave correctly with non-streamed bodies" in {

      val uri = Uri(new URI(s"$svcUrlBase/leergut.redeem"))
      val request = sttp
        .body("test")
        .contentType("application/json")
        .post(uri)

      val response = Await.result(request.send(), 3.seconds)

      assert(response.code == StatusCodes.Ok)
    }
  }
}
