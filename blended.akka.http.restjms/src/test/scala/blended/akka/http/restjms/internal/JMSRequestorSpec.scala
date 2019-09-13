package blended.akka.http.restjms.internal

import java.io.File
import java.net.URI

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import blended.activemq.brokerstarter.internal.BrokerActivator
import blended.akka.http.internal.BlendedAkkaHttpActivator
import blended.akka.http.restjms.AkkaHttpRestJmsActivator
import blended.akka.internal.BlendedAkkaActivator
import blended.jms.utils._
import blended.testsupport.BlendedTestSupport
import blended.testsupport.pojosr.{PojoSrTestHelper, SimplePojoContainerSpec}
import com.softwaremill.sttp._
import com.softwaremill.sttp.{StatusCodes => SttpStatusCodes}
import org.osgi.framework.BundleActivator
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}

import scala.concurrent.duration._

class JMSRequestorSpec extends SimplePojoContainerSpec
  with WordSpecLike
  with Matchers
  with PojoSrTestHelper
  with BeforeAndAfterAll {

  override def baseDir : String = new File(BlendedTestSupport.projectTestOutput, "container").getAbsolutePath()

  override def bundles : Seq[(String, BundleActivator)] = Seq(
    "blended.akka" -> new BlendedAkkaActivator(),
    "blended.activemq.brokerstarter" -> new BrokerActivator(),
    "blended.akka.http" -> new BlendedAkkaHttpActivator(),
    "blended.akka.http.restjms" -> new AkkaHttpRestJmsActivator()
  )

  private val svcUrlBase : String = "http://localhost:9995/restjms"

  private implicit val backend : SttpBackend[Id, _] = HttpURLConnectionBackend()
  private implicit val timeout : FiniteDuration = 3.seconds

  private implicit val system : ActorSystem = mandatoryService[ActorSystem](registry)(None)
  private implicit val materializer : ActorMaterializer = ActorMaterializer()

  private val cf : IdAwareConnectionFactory = mandatoryService[IdAwareConnectionFactory](registry)(None)

  private val responder : JMSResponder = new JMSResponder(cf)
  responder.start()

  def performRequest(uri : String, cType : String, body : String) : RequestT[Id, String, Nothing] = {
    sttp.post(Uri(new URI(s"$svcUrlBase/$uri")))
      .body(body)
      .header("Content-Type", cType, true)
  }

  "The JMSRequestor" should {

    "respond to a posted message if the operation is configured [json]" in {
      val response = performRequest("leergut.redeem", "application/json", "test" * 1000).send()

      response.code should be (SttpStatusCodes.Ok)
      response.unsafeBody should be (MockResponses.json)
      response.header("Content-Type") should be (Some("application/json"))
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

    "respond with a not found return code if the operation is not configured" in {
      val response = performRequest("dummy", "application/json", "test" * 1000).send()
      response.code should be (SttpStatusCodes.NotFound)
    }

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

    "respond with a server error if the Content Type is invalid" in {
      val response = performRequest("leergut.redeem", "text/plain", "test" * 1000).send()
      response.code should be (com.softwaremill.sttp.StatusCodes.InternalServerError)
    }

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

    "behave correctly with streamed or chunked bodies" in {
      pending
      //      val uri = Uri(new URI(s"http://localhost:$port/leergut.redeem"))
      //      val request = sttp
      //        .streamBody(Source.single(ByteString("test")))
      //        //.body("test")
      //        .contentType("application/json")
      //        .post(uri)
      //
      //      val response = Await.result(request.send(), 3.seconds)
      //
      //      assert(response.code == StatusCodes.Ok)
    }

    "behave correctly with non-streamed bodies" in {
      pending
      //      val uri = Uri(new URI(s"http://localhost:$port/leergut.redeem"))
      //      val request = sttp
      //        .body("test")
      //        .contentType("application/json")
      //        .post(uri)
      //
      //      val response = Await.result(request.send(), 3.seconds)
      //
      //      assert(response.code == StatusCodes.Ok)
    }
  }
}
