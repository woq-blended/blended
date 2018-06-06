package blended.akka.http.restjms.internal

import akka.http.scaladsl.model._
import akka.http.scaladsl.testkit.ScalatestRouteTest
import akka.stream.ActorMaterializer
import akka.util.ByteString
import blended.camel.utils.BlendedCamelContextFactory
import com.typesafe.config.ConfigFactory
import org.apache.activemq.ActiveMQConnectionFactory
import org.apache.camel.CamelContext
import org.apache.camel.builder.RouteBuilder
import org.apache.camel.component.jms.JmsComponent
import org.scalatest.{Matchers, WordSpec}

import scala.concurrent.Await
import scala.concurrent.duration._

class JMSRequestorSpec extends WordSpec
  with Matchers
  with ScalatestRouteTest {

  val amqCF = new ActiveMQConnectionFactory("vm://dispatcher?broker.useJmx=false&broker.persistent=false&create=true")
  val cfg = ConfigFactory.load("restjms.conf")
  val restJmsConfig = RestJMSConfig.fromConfig(cfg)


  val camelContext: CamelContext = {
    val result = BlendedCamelContextFactory.createContext(withJmx = false)

    result.addComponent("jms", JmsComponent.jmsComponent(amqCF))

    result.addRoutes(new RouteBuilder() {
      override def configure(): Unit = {
        from("jms:queue:redeem")
          .to("log:redeem?showHeaders=true")
          .setBody(constant("redeemed"))
      }
    })

    result.start()

    result
  }

  implicit val as = system
  val svc = new SimpleRestJmsService(restJmsConfig.operations, camelContext, ActorMaterializer(), system.dispatcher)

  "The JMSRequestor" should {

    "respond to a posted message if the operation is configured" in {

      HttpRequest(
        method = HttpMethods.POST,
        uri = "/leergut.redeem",
        entity = HttpEntity.Strict(ContentTypes.`application/json`, ByteString("test"))
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
        entity = HttpEntity.Strict(ContentTypes.`application/json`, ByteString("test"))
      ) ~> svc.httpRoute ~> check {
        contentType should be (ContentTypes.`application/json`)
        status should be (StatusCodes.NotFound)
      }
    }

    "respond with a server error if the JMS request times out" in {
      HttpRequest(
        method = HttpMethods.POST,
        uri = "/foo",
        entity = HttpEntity.Strict(ContentTypes.`application/json`, ByteString("test"))
      ) ~> svc.httpRoute ~> check {
        contentType should be (ContentTypes.`application/json`)
        status should be (StatusCodes.InternalServerError)
      }
    }

    "respond with a server error if the Content Type is invalid" in {
      HttpRequest(
        method = HttpMethods.POST,
        uri = "/leergut.redeem",
        entity = HttpEntity.Strict(ContentTypes.`text/plain(UTF-8)`, ByteString("test"))
      ) ~> svc.httpRoute ~> check {
        status should be (StatusCodes.InternalServerError)
      }
    }

    "respond directly with OK and an empty body if 'jmsreply' is set to false in the config" in {
      HttpRequest(
        method = HttpMethods.POST,
        uri = "/direct",
        entity = HttpEntity.Strict(ContentTypes.`application/json`, ByteString("test"))
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
        entity = HttpEntity.Strict(cType, ByteString("test"))
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
