package blended.akka.http.restjms.internal

import akka.actor.ActorSystem
import blended.jms.utils.{IdAwareConnectionFactory, JmsDestination}
import blended.streams.jms.JmsStreamSupport
import blended.streams.message.FlowEnvelope
import blended.streams.processor.Collector
import blended.testsupport.RequiresForkedJVM
import blended.util.logging.Logger
import sttp.client._
import sttp.model.StatusCode

import scala.util.Try
import scala.concurrent.Await
import scala.concurrent.duration._

@RequiresForkedJVM
class JMSRequestorSpec extends AbstractJmsRequestorSpec with JmsStreamSupport {

  private val log : Logger = Logger[JMSRequestorSpec]
  private implicit val backend = HttpURLConnectionBackend()

  private def performRequest(uri : String, cType : String, body : String) = {
    log.info(s"Performing post to [$uri] : [$body]")
    basicRequest.post(uri"${plainServerUrl(registry)}/restjms/$uri")
      .body(body)
      .header("Content-Type", cType, true)
  }

  private def consumeMessages(
    destName : String,
    completeOn : Option[Seq[FlowEnvelope] => Boolean],
    timeout : FiniteDuration
  )(implicit system : ActorSystem) : Try[List[FlowEnvelope]] = Try {

    val cf : IdAwareConnectionFactory = jmsConnectionFactory(registry, mustConnect = true, timeout = timeout).get

    val coll : Collector[FlowEnvelope] = receiveMessages(
      headerCfg = headerCfg,
      cf = cf,
      dest = JmsDestination.create(destName).get,
      log = envLogger(log),
      completeOn = completeOn,
      timeout = Some(timeout),
      ackTimeout = 1.second
    )
    Await.result(coll.result, timeout + 100.millis)
  }

  "The JMSRequestor should" - {

    "respond to a posted message if the operation is configured [json]" in {
      val response = performRequest("leergut.redeem", "application/json", "test" * 1000).send()

      response.code should be (StatusCode.Ok)
      response.body should be (Right(MockResponses.json))
      response.header("Content-Type") should be (Some("application/json"))

      val wiretapped =
        consumeMessages("bridge.data.in", Some(_.nonEmpty), 10.seconds)(actorSystem).get

      wiretapped should not be(empty)
    }

    "respond to a posted message if the operation is configured [xml]" in {
      val response = performRequest("leergut.redeem", "text/xml", "test" * 1000).send()

      response.code should be (StatusCode.Ok)
      response.body should be (Right(MockResponses.xml))
      response.header("Content-Type") should be (Some("text/xml"))

      val wiretapped =
        consumeMessages("bridge.data.in", Some(_.nonEmpty), 10.seconds)(actorSystem).get

      wiretapped should not be(empty)
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

      val wiretapped =
        consumeMessages("bridge.data.in", Some(_.nonEmpty), 10.seconds)(actorSystem).get

      wiretapped should not be(empty)
    }

    "respond directly with Accepted and an empty body if 'jmsreply' is set to false and isSoap is set to true in the config" in {
      val response = performRequest("soap", "text/xml", "test" * 1000).send()

      response.code should be (StatusCode.Accepted)
      response.body should be (Right(""))
      response.header("Content-Type") should be (Some("text/xml"))
    }
  }
}
