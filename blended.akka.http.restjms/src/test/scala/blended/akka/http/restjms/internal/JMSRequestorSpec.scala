package blended.akka.http.restjms.internal

import akka.http.scaladsl.model._
import akka.http.scaladsl.testkit.{RouteTestTimeout, ScalatestRouteTest}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Source
import akka.util.ByteString
import org.scalatest.{Matchers, WordSpec}

import scala.concurrent.Await
import scala.concurrent.duration._

class JMSRequestorSpec extends WordSpec
  with Matchers
  with ScalatestRouteTest
  with JMSRequestorSetup {

  implicit val executionContext = system.dispatcher
  val svc = new SimpleRestJmsService(restJmsConfig.operations, camelContext, ActorMaterializer(), system.dispatcher)

  def requestEntity(cType: ContentType, body: String) : RequestEntity =
    HttpEntity.Default(cType, body.length(), Source.single(ByteString(body)))

  "The JMSRequestor" should {

    "respond to a posted message if the operation is configured" in {

      HttpRequest(
        method = HttpMethods.POST,
        uri = "/leergut.redeem",
        entity = requestEntity(ContentTypes.`application/json`, "test"*1000)
      ) ~> svc.httpRoute ~> check {
        contentType should be (ContentTypes.`application/json`)
        responseAs[String] should be ("redeemed")
        status should be (StatusCodes.OK)
      }
    }

    "respond with a not found return code if the operation is not configured" in {

      HttpRequest(
        method = HttpMethods.POST,
        uri = "/noop",
        entity = requestEntity(ContentTypes.`application/json`, "test")
      ) ~> svc.httpRoute ~> check {
        contentType should be (ContentTypes.`application/json`)
        status should be (StatusCodes.NotFound)
      }
    }

    "respond with a server error if the JMS request times out" in {

      implicit val timeout : RouteTestTimeout = RouteTestTimeout(3.seconds)

      HttpRequest(
        method = HttpMethods.POST,
        uri = "/foo",
        entity = requestEntity(ContentTypes.`application/json`, "test")
      ) ~> svc.httpRoute ~> check {
        contentType should be (ContentTypes.`application/json`)
        status should be (StatusCodes.InternalServerError)
      }
    }

    "respond with a server error if the Content Type is invalid" in {
      HttpRequest(
        method = HttpMethods.POST,
        uri = "/leergut.redeem",
        entity = requestEntity(ContentTypes.`text/plain(UTF-8)`, "test")
      ) ~> svc.httpRoute ~> check {
        status should be (StatusCodes.InternalServerError)
      }
    }

    "respond directly with OK and an empty body if 'jmsreply' is set to false in the config" in {
      HttpRequest(
        method = HttpMethods.POST,
        uri = "/direct",
        entity = requestEntity(ContentTypes.`application/json`, "test")
      ) ~> svc.httpRoute ~> check {
        responseAs[String] should be ("")
        contentType should be (ContentTypes.`application/json`)
        status should be (StatusCodes.OK)
      }
    }

    "respond directly with Accepted and an empty body if 'jmsreply' is set to false and isSoap is set to true in the config" in {

      val mType : MediaType.WithFixedCharset = MediaType.textWithFixedCharset( "xml", HttpCharsets.`UTF-8` )
      val cType : ContentType = ContentType(mType)

      HttpRequest(
        method = HttpMethods.POST,
        uri = "/soap",
        entity = requestEntity(cType, "test")
      ) ~> svc.httpRoute ~> check {
        contentType should be (cType)
        responseAs[String] should be ("")
        status should be (StatusCodes.Accepted)
      }
    }
  }

  override def cleanUp(): Unit = {
    Await.result(system.terminate(), 10.seconds)
  }
}
