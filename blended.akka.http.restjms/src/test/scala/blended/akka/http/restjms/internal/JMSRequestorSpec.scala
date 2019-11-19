package blended.akka.http.restjms.internal

import java.net.URI

import com.softwaremill.sttp.{StatusCodes => SttpStatusCodes, _}

class JMSRequestorSpec extends AbstractJmsRequestorSpec {

  private implicit val backend = HttpURLConnectionBackend()

  def performRequest(uri : String, cType : String, body : String) : RequestT[Id, String, Nothing] = {
    sttp.post(Uri(new URI(s"$svcUrlBase/$uri")))
      .body(body)
      .header("Content-Type", cType, true)
  }

  "The JMSRequestor should" - {

    "respond to a posted message if the operation is configured [json]" in {
      val response = performRequest("leergut.redeem", "application/json", "test" * 1000).send()

      response.code should be (SttpStatusCodes.Ok)
      response.unsafeBody should be (MockResponses.json)
      response.header("Content-Type") should be (Some("application/json"))
    }

    "respond to a posted message if the operation is configured [xml]" in {
      val response = performRequest("leergut.redeem", "text/xml", "test" * 1000).send()

      response.code should be (SttpStatusCodes.Ok)
      response.unsafeBody should be (MockResponses.xml)
      response.header("Content-Type") should be (Some("text/xml"))
    }

    "respond with a not found return code if the operation is not configured" in {
      val response = performRequest("dummy", "application/json", "test" * 1000).send()
      response.code should be (SttpStatusCodes.NotFound)
    }

    "respond with a server error if the JMS request times out" in {
      val response = performRequest("foo", "application/json", "test" * 1000).send()

      response.code should be (SttpStatusCodes.InternalServerError)
      response.header("Content-Type") should be (Some("application/json"))
    }

    "respond with a server error if the Content Type is invalid" in {
      val response = performRequest("leergut.redeem", "text/plain", "test" * 1000).send()
      response.code should be (com.softwaremill.sttp.StatusCodes.InternalServerError)
    }

    "respond directly with OK and an empty body if 'jmsreply' is set to false in the config" in {
      val response = performRequest("direct", "application/json", "test" * 1000).send()

      response.code should be (SttpStatusCodes.Ok)
      response.unsafeBody should be ("")
      response.header("Content-Type") should be (Some("application/json"))
    }

    "respond directly with Accepted and an empty body if 'jmsreply' is set to false and isSoap is set to true in the config" in {
      val response = performRequest("soap", "text/xml", "test" * 1000).send()

      response.code should be (SttpStatusCodes.Accepted)
      response.unsafeBody should be ("")
      response.header("Content-Type") should be (Some("text/xml"))
    }
  }
}
