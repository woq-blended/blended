package blended.akka.http.restjms.internal

import blended.testsupport.RequiresForkedJVM
import blended.util.logging.Logger
import sttp.client._
import sttp.model.StatusCode

@RequiresForkedJVM
class JMSRequestorSpec extends AbstractJmsRequestorSpec {

  private val log : Logger = Logger[JMSRequestorSpec]
  private implicit val backend = HttpURLConnectionBackend()

  def performRequest(uri : String, cType : String, body : String) = {
    log.info(s"Performing post to [$uri] : [$body]")
    basicRequest.post(uri"$svcUrlBase/$uri")
      .body(body)
      .header("Content-Type", cType, true)
  }

  "The JMSRequestor should" - {

    "respond to a posted message if the operation is configured [json]" in {
      val response = performRequest("leergut.redeem", "application/json", "test" * 1000).send()

      response.code should be (StatusCode.Ok)
      response.body should be (Right(MockResponses.json))
      response.header("Content-Type") should be (Some("application/json"))
    }

    "respond to a posted message if the operation is configured [xml]" in {
      val response = performRequest("leergut.redeem", "text/xml", "test" * 1000).send()

      response.code should be (StatusCode.Ok)
      response.body should be (Right(MockResponses.xml))
      response.header("Content-Type") should be (Some("text/xml"))
    }

    "respond with a not found return code if the operation is not configured" in {
      val response = performRequest("dummy", "application/json", "test" * 1000).send()
      response.code should be (StatusCode.NotFound)
    }

    "respond with a server error if the JMS request times out" in {
      val response = performRequest("foo", "application/json", "test" * 1000).send()

      response.code should be (StatusCode.InternalServerError)
      response.header("Content-Type") should be (Some("application/json"))
    }

    "respond with a server error if the Content Type is invalid" in {
      val response = performRequest("leergut.redeem", "text/plain", "test" * 1000).send()
      response.code should be (StatusCode.InternalServerError)
    }

    "respond directly with OK and an empty body if 'jmsreply' is set to false in the config" in {
      val response = performRequest("direct", "application/json", "test" * 1000).send()

      response.code should be (StatusCode.Ok)
      response.body should be (Right(""))
      response.header("Content-Type") should be (Some("application/json"))
    }

    "respond directly with Accepted and an empty body if 'jmsreply' is set to false and isSoap is set to true in the config" in {
      val response = performRequest("soap", "text/xml", "test" * 1000).send()

      response.code should be (StatusCode.Accepted)
      response.body should be (Right(""))
      response.header("Content-Type") should be (Some("text/xml"))
    }
  }
}
