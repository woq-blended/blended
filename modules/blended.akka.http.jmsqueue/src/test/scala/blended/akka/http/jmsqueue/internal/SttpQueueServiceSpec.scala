package blended.akka.http.jmsqueue.internal

import java.io.File
import java.net.URI

import akka.util.ByteString
import blended.activemq.brokerstarter.internal.BrokerActivator
import blended.akka.http.internal.BlendedAkkaHttpActivator
import blended.akka.http.jmsqueue.BlendedAkkaHttpJmsqueueActivator
import blended.akka.internal.BlendedAkkaActivator
import blended.jms.utils.{IdAwareConnectionFactory, JmsQueue}
import blended.jmx.internal.BlendedJmxActivator
import blended.streams.jms.{JmsProducerSettings, JmsStreamSupport}
import blended.streams.message.{FlowEnvelope, FlowMessage}
import blended.testsupport.BlendedTestSupport
import blended.testsupport.pojosr.{AkkaHttpServerTestHelper, BlendedPojoRegistry, JmsConnectionHelper, PojoSrTestHelper, SimplePojoContainerSpec}
import blended.testsupport.scalatest.LoggingFreeSpecLike
import blended.util.logging.Logger
import org.osgi.framework.BundleActivator
import org.scalatest.matchers.should.Matchers
import sttp.client._
import sttp.model.{StatusCode, Uri}

import scala.util.Right

// ToDo: Enhance with Property based test
class SttpQueueServiceSpec extends SimplePojoContainerSpec
  with LoggingFreeSpecLike
  with Matchers
  with PojoSrTestHelper
  with JmsStreamSupport
  with AkkaHttpServerTestHelper
  with JmsConnectionHelper {

  private val log : Logger = Logger[SttpQueueServiceSpec]

  private implicit val backend = HttpURLConnectionBackend()

  private val svcUrlBase : BlendedPojoRegistry => String = r => s"${plainServerUrl(r)}/httpqueue"

  override def baseDir : String = new File(BlendedTestSupport.projectTestOutput, "container").getAbsolutePath()

  override def bundles : Seq[(String, BundleActivator)] = Seq(
    "blended.jmx" -> new BlendedJmxActivator(),
    "blended.akka" -> new BlendedAkkaActivator(),
    "blended.activemq.brokerstarter" -> new BrokerActivator(),
    "blended.akka.http" -> new BlendedAkkaHttpActivator(),
    "blended.akka.http.jmsqueue" -> new BlendedAkkaHttpJmsqueueActivator()
  )

  private def amqCf(r : BlendedPojoRegistry) : IdAwareConnectionFactory = jmsConnectionFactory(r, mustConnect = true, timeout).get

  "The Http Queue Service should (STTP client)" - {

    "respond with a bad request if the url does not match [provider/queue]" in logException {
      val request = basicRequest.get(Uri(new URI(s"${svcUrlBase(registry)}/foo")))
      val response = request.send()

      response.code should be(StatusCode.BadRequest)
    }

    "respond with Unauthorised if the requested Queue is not configured" in logException {
      val request = basicRequest.get(Uri(new URI(s"${svcUrlBase(registry)}/activemq/bar")))
      val response = request.send()

      response.code should be(StatusCode.Unauthorized)
    }

    "respond with Unauthorised if the JMS provider is unknown or doesn't have any queues configured" in logException {
      val request = basicRequest.get(Uri(new URI(s"${svcUrlBase(registry)}/sonic/foo")))
      val response = request.send()

      response.code should be(StatusCode.Unauthorized)
    }

    "respond with an empty response if no msg is available" in logException {
      val request = basicRequest.get(Uri(new URI(s"${svcUrlBase(registry)}/blended/Queue1")))
      val response = request.send()

      response.code should be(StatusCode.NoContent)
    }

    "respond with a text response if the queue contains a text message" in logException {

      val msg : String = "Hello Blended"
      val env : FlowEnvelope = FlowEnvelope(FlowMessage(msg)(FlowMessage.noProps))

      val pSettings : JmsProducerSettings = JmsProducerSettings(
        log = envLogger(log),
        headerCfg = headerCfg,
        connectionFactory = amqCf(registry),
        jmsDestination = Some(JmsQueue("Queue1"))
      )

      sendMessages(pSettings, envLogger(log), env)(actorSystem)

      val request = basicRequest.get(Uri(new URI(s"${svcUrlBase(registry)}/blended/Queue1")))
      val response = request.send()

      response.code should be(StatusCode.Ok)
      response.body should be(Right("Hello Blended"))
      response.contentType should be(defined)
      assert(response.contentType.forall(_.startsWith("text/plain")))
    }

    "respond with a binary response if the queue contains a binary message" in logException {

      val msg : String = "Hello Blended"
      val env : FlowEnvelope = FlowEnvelope(FlowMessage(ByteString(msg))(FlowMessage.noProps))

      val pSettings : JmsProducerSettings = JmsProducerSettings(
        log = envLogger(log),
        headerCfg = headerCfg,
        connectionFactory = amqCf(registry),
        jmsDestination = Some(JmsQueue("Queue1"))
      )

      sendMessages(pSettings, envLogger(log), env)(actorSystem)

      val request = basicRequest.get(Uri(new URI(s"${svcUrlBase(registry)}/blended/Queue1")))
      val response = request.send()

      response.code should be(StatusCode.Ok)
      response.body should be(Right("Hello Blended"))
      response.contentType should be(defined)
      assert(response.contentType.forall(_.startsWith("application/octet-stream")))
    }

    "allow null values for JMS message properties" in logException {
      val msg : String = "Hello Blended"
      val env : FlowEnvelope = FlowEnvelope(
        FlowMessage(ByteString(msg))(FlowMessage.props("iamnull" -> null).get)
      )

      val pSettings : JmsProducerSettings = JmsProducerSettings(
        log = envLogger(log),
        headerCfg = headerCfg,
        connectionFactory = amqCf(registry),
        jmsDestination = Some(JmsQueue("Queue1"))
      )

      sendMessages(pSettings, envLogger(log), env)(actorSystem)

      val request = basicRequest.get(Uri(new URI(s"${svcUrlBase(registry)}/blended/Queue1")))
      val response = request.send()

      response.code should be (StatusCode.Ok)
      response.body should be (Right("Hello Blended"))
      response.contentType should be (defined)
      assert(response.contentType.forall(_.startsWith("application/octet-stream")))
    }
  }
}
