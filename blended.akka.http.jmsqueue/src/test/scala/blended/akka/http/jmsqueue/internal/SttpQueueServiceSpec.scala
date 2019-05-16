package blended.akka.http.jmsqueue.internal

import java.io.File
import java.net.URI

import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, Materializer}
import akka.util.ByteString
import blended.activemq.brokerstarter.internal.BrokerActivator
import blended.akka.http.HttpContext
import blended.akka.http.internal.BlendedAkkaHttpActivator
import blended.akka.http.jmsqueue.BlendedAkkaHttpJmsqueueActivator
import blended.akka.internal.BlendedAkkaActivator
import blended.jms.utils.{IdAwareConnectionFactory, JmsQueue}
import blended.streams.jms.{JmsProducerSettings, JmsStreamSupport}
import blended.streams.message.{FlowEnvelope, FlowMessage}
import blended.streams.transaction.FlowHeaderConfig
import blended.testsupport.BlendedTestSupport
import blended.testsupport.pojosr.{PojoSrTestHelper, SimplePojoContainerSpec}
import blended.testsupport.scalatest.LoggingFreeSpecLike
import blended.util.logging.Logger
import com.softwaremill.sttp._
import org.osgi.framework.BundleActivator
import org.scalatest.Matchers

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.{FiniteDuration, _}
import scala.util.Right

class SttpQueueServiceSpec extends SimplePojoContainerSpec
  with LoggingFreeSpecLike
  with Matchers
  with PojoSrTestHelper
  with JmsStreamSupport {

  private val log : Logger = Logger[SttpQueueServiceSpec]

  private implicit val timeout : FiniteDuration = 3.seconds
  private implicit val backend = HttpURLConnectionBackend()

  private val svcUrlBase : String = "http://localhost:9995/httpqueue"

  private val headerCfg : FlowHeaderConfig = FlowHeaderConfig.create("App")

  override def baseDir: String = new File(BlendedTestSupport.projectTestOutput, "container").getAbsolutePath()

  override def bundles: Seq[(String, BundleActivator)] = Seq(
    "blended.akka" -> new BlendedAkkaActivator(),
    "blended.activemq.brokerstarter" -> new BrokerActivator(),
    "blended.akka.http" -> new BlendedAkkaHttpActivator(),
    "blended.akka.http.jmsqueue" -> new BlendedAkkaHttpJmsqueueActivator()
  )

  private[this] val amqCF : IdAwareConnectionFactory = mandatoryService[IdAwareConnectionFactory](registry)(None)
  private[this] val httpCtxt : HttpContext = mandatoryService[HttpContext](registry)(None)
  private implicit val system : ActorSystem = mandatoryService[ActorSystem](registry)(None)
  private implicit val eCtxt : ExecutionContext = system.dispatcher
  private implicit val materializer : Materializer = ActorMaterializer()

  "The Http Queue Service should (STTP client)" - {

    "respond with a bad request if the url does not match [provider/queue]" in {
      val request = sttp.get(Uri(new URI(s"$svcUrlBase/foo")))
      val response = request.send()

      response.code should be (StatusCodes.BadRequest)
    }

    "respond with Unauthorised if the requested Queue is not configured" in {
      val request = sttp.get(Uri(new URI(s"$svcUrlBase/activemq/bar")))
      val response = request.send()

      response.code should be (StatusCodes.Unauthorized)
    }

    "respond with Unauthorised if the JMS provider is unknown or doesn't have any queues configured" in {
      val request = sttp.get(Uri(new URI(s"$svcUrlBase/sonic/foo")))
      val response = request.send()

      response.code should be (StatusCodes.Unauthorized)
    }

    "respond with an empty response if no msg is available" in {
      val request = sttp.get(Uri(new URI(s"$svcUrlBase/blended/Queue1")))
      val response = request.send()

      response.code should be (StatusCodes.NoContent)
    }

    "respond with a text response if the queue contains a text message" in {

      val msg : String = "Hello Blended"
      val env : FlowEnvelope = FlowEnvelope(FlowMessage(msg)(FlowMessage.noProps))

      val pSettings : JmsProducerSettings = JmsProducerSettings(
        log = log,
        headerCfg = headerCfg,
        connectionFactory = amqCF,
        jmsDestination = Some(JmsQueue("Queue1"))
      )

      sendMessages(pSettings, log, env)

      val request = sttp.get(Uri(new URI(s"$svcUrlBase/blended/Queue1")))
      val response = request.send()

      response.code should be (StatusCodes.Ok)
      response.body should be (Right("Hello Blended"))
      response.contentType should be (defined)
      assert(response.contentType.forall(_.startsWith("text/plain")))
    }

    "respond with a binary response if the queue contains a binary message" in {

      val msg : String = "Hello Blended"
      val env : FlowEnvelope = FlowEnvelope(FlowMessage(ByteString(msg))(FlowMessage.noProps))

      val pSettings : JmsProducerSettings = JmsProducerSettings(
        log = log,
        headerCfg = headerCfg,
        connectionFactory = amqCF,
        jmsDestination = Some(JmsQueue("Queue1"))
      )

      sendMessages(pSettings, log, env)

      val request = sttp.get(Uri(new URI(s"$svcUrlBase/blended/Queue1")))
      val response = request.send()

      response.code should be (StatusCodes.Ok)
      response.body should be (Right("Hello Blended"))
      response.contentType should be (defined)
      assert(response.contentType.forall(_.startsWith("application/octet-stream")))
    }
  }
}
