package blended.akka.http.restjms.internal

import akka.http.scaladsl.model._
import akka.http.scaladsl.testkit.{RouteTestTimeout, ScalatestRouteTest}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Source
import akka.util.ByteString
import blended.jms.utils.{IdAwareConnectionFactory, SimpleIdAwareConnectionFactory}
import com.typesafe.config.{Config, ConfigFactory}
import javax.jms.ConnectionFactory
import org.apache.activemq.ActiveMQConnectionFactory
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpec}

import scala.concurrent.Await
import scala.concurrent.duration._

class JMSRequestorSpec extends WordSpec
  with Matchers
  with ScalatestRouteTest
  with BeforeAndAfterAll {

  private val cfg : Config = ConfigFactory.load("restjms.conf")
  private val restJmsConfig : RestJMSConfig = RestJMSConfig.fromConfig(cfg)
  private val amqCF : ConnectionFactory = new ActiveMQConnectionFactory("vm://dispatcher?broker.useJmx=false&broker.persistent=false&create=true")
  private val cf : IdAwareConnectionFactory = SimpleIdAwareConnectionFactory("amq", "amq", "requestor", amqCF, 5.seconds)
  private val responder : JMSResponder = new JMSResponder(cf)
  responder.start()

  private val svc = new SimpleRestJmsService(restJmsConfig.operations, ActorMaterializer(), system.dispatcher)


  override protected def beforeAll(): Unit = {
    Thread.sleep(5000)
  }

  def requestEntity(cType : ContentType, body : String) : RequestEntity =
    HttpEntity.Default(cType, body.length(), Source.single(ByteString(body)))

  "The JMSRequestor" should {

    "respond to a posted message if the operation is configured [json]" in {

      HttpRequest(
        method = HttpMethods.POST,
        uri = "/leergut.redeem",
        entity = requestEntity(ContentTypes.`application/json`, "test" * 1000)
      ) ~> svc.httpRoute ~> check {
        status should be(StatusCodes.OK)
        contentType should be(ContentTypes.`application/json`)
        responseAs[String] should be(MockResponses.json)
      }
    }

    "respond to a posted message if the operation is configured [xml]" in {
      pending
//
//      HttpRequest(
//        method = HttpMethods.POST,
//        uri = "/leergut.redeem",
//        entity = requestEntity(ContentTypes.`text/xml(UTF-8)`, "test" * 1000)
//      ) ~> svc.httpRoute ~> check {
//          contentType should be(ContentTypes.`text/xml(UTF-8)`)
//          responseAs[String] should be(MockResponses.xml)
//          status should be(StatusCodes.OK)
//        }
    }
//
    "respond with a not found return code if the operation is not configured" in {
      pending
//
//      HttpRequest(
//        method = HttpMethods.POST,
//        uri = "/noop",
//        entity = requestEntity(ContentTypes.`application/json`, "test")
//      ) ~> svc.httpRoute ~> check {
//          contentType should be(ContentTypes.`application/json`)
//          status should be(StatusCodes.NotFound)
//        }
    }
//
    "respond with a server error if the JMS request times out" in {
      pending
//      implicit val timeout : RouteTestTimeout = RouteTestTimeout(3.seconds)
//
//      HttpRequest(
//        method = HttpMethods.POST,
//        uri = "/foo",
//        entity = requestEntity(ContentTypes.`application/json`, "test")
//      ) ~> svc.httpRoute ~> check {
//          contentType should be(ContentTypes.`application/json`)
//          status should be(StatusCodes.InternalServerError)
//        }
    }
//
    "respond with a server error if the Content Type is invalid" in {
      pending
//      HttpRequest(
//        method = HttpMethods.POST,
//        uri = "/leergut.redeem",
//        entity = requestEntity(ContentTypes.`text/plain(UTF-8)`, "test")
//      ) ~> svc.httpRoute ~> check {
//          status should be(StatusCodes.InternalServerError)
//        }
    }
//
    "respond directly with OK and an empty body if 'jmsreply' is set to false in the config" in {
      pending
//      HttpRequest(
//        method = HttpMethods.POST,
//        uri = "/direct",
//        entity = requestEntity(ContentTypes.`application/json`, "test")
//      ) ~> svc.httpRoute ~> check {
//          responseAs[String] should be("")
//          contentType should be(ContentTypes.`application/json`)
//          status should be(StatusCodes.OK)
//        }
    }
//
    "respond directly with Accepted and an empty body if 'jmsreply' is set to false and isSoap is set to true in the config" in {
      pending
//
//      val mType : MediaType.WithFixedCharset = MediaType.textWithFixedCharset("xml", HttpCharsets.`UTF-8`)
//      val cType : ContentType = ContentType(mType)
//
//      HttpRequest(
//        method = HttpMethods.POST,
//        uri = "/soap",
//        entity = requestEntity(cType, "test")
//      ) ~> svc.httpRoute ~> check {
//          contentType should be(cType)
//          responseAs[String] should be("")
//          status should be(StatusCodes.Accepted)
//        }
    }
  }

  override def cleanUp() : Unit = {
    Await.result(system.terminate(), 10.seconds)
  }
}
