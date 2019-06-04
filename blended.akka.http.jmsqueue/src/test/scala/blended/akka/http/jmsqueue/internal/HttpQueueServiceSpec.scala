package blended.akka.http.jmsqueue.internal

import akka.http.scaladsl.model.{ContentTypes, StatusCodes}
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.testkit.ScalatestRouteTest
import akka.stream.scaladsl.{Keep, Sink}
import akka.stream.{ActorMaterializer, Materializer}
import akka.util.ByteString
import blended.jms.utils.{IdAwareConnectionFactory, JmsQueue}
import blended.streams.jms.{JmsProducerSettings, JmsStreamSupport}
import blended.streams.message.{FlowEnvelope, FlowMessage}
import blended.streams.transaction.FlowHeaderConfig
import blended.util.logging.Logger
import com.typesafe.config.ConfigFactory
import javax.jms.ConnectionFactory
import org.apache.activemq.broker.BrokerService
import org.scalatest.{BeforeAndAfterAll, FreeSpec, FreeSpecLike, Matchers}

import scala.concurrent.{Await, ExecutionContext}
import scala.concurrent.duration._

class HttpQueueServiceSpec extends FreeSpec
  with ScalatestRouteTest
  with FreeSpecLike
  with Matchers
  with AmqBrokerSupport
  with BeforeAndAfterAll
  with JmsStreamSupport {

  private val log : Logger = Logger[HttpQueueServiceSpec]

  private val broker : BrokerService = startBroker()
  private val cf : IdAwareConnectionFactory = amqCf()
  private val maerialzer : Materializer = ActorMaterializer()

  private val headerCfg : FlowHeaderConfig = FlowHeaderConfig.create("App")

  override protected def afterAll() : Unit = {
    stopBroker(broker)
  }

  private val cfg = ConfigFactory.load("httpqueue.conf").resolve()
  private val qCfg = HttpQueueConfig.fromConfig(cfg)

  private val queues : List[String] = List("Queue1", "Queue2")

  private val route : Route = new HttpQueueService {
    override implicit val eCtxt : ExecutionContext = system.dispatcher
    override val qConfig : HttpQueueConfig = qCfg

    override def withConnectionFactory[T](vendor : String, provider : String)(f : Option[ConnectionFactory] => T) : T = f(Some(cf))
  }.httpRoute

  "The Http Queue Service should" - {

    "respond with a bad request if the url does not match [provider/queue]" in {
      Get("/foo") ~> route ~> check {
        response.status should be(StatusCodes.BadRequest)
      }
    }

    "respond with Unauthorised if the requested Queue is not configured" in {
      Get("/cc_queue/bar") ~> route ~> check {
        response.status should be(StatusCodes.Unauthorized)
      }
    }

    "respond with Unauthorised if the JMS provider is unknown or doesn't have any queues configured" in {
      Get("/foo/bar") ~> route ~> check {
        response.status should be(StatusCodes.Unauthorized)
      }
    }

    "respond with an empty response if no msg is available" in {
      queues.foreach { q =>
        Get(s"/activemq/$q") ~> route ~> check {
          response.status should be(StatusCodes.NoContent)
        }
      }
    }

    "respond with a text message if the queue contains a text message" in {

      val msg : String = "Hello Blended"
      val env : FlowEnvelope = FlowEnvelope(FlowMessage(msg)(FlowMessage.noProps))

      val pSettings : JmsProducerSettings = JmsProducerSettings(
        log = log,
        headerCfg = headerCfg,
        connectionFactory = cf,
        jmsDestination = Some(JmsQueue("Queue1"))
      )

      sendMessages(pSettings, log, env)

      Get(s"/activemq/Queue1") ~> route ~> check {
        val r = response
        r.status should be(StatusCodes.OK)
        r.entity.contentType should be(ContentTypes.`text/plain(UTF-8)`)

        val g = r.entity.dataBytes
          .toMat(Sink.seq)(Keep.right)

        val vf = r.entity.dataBytes.toMat(Sink.seq[ByteString])(Keep.right).run()

        val v : ByteString = Await.result(vf, 1.second).foldLeft(ByteString("")) { case (c, e) => c.concat(e) }
        v should be(ByteString(msg))
      }
    }

    "respond with a binary message if the queue contains a byte message" in {
      val msg : String = "Hello Blended"
      val env : FlowEnvelope = FlowEnvelope(FlowMessage(ByteString(msg))(FlowMessage.noProps))

      val pSettings : JmsProducerSettings = JmsProducerSettings(
        log = log,
        headerCfg = headerCfg,
        connectionFactory = cf,
        jmsDestination = Some(JmsQueue("Queue1"))
      )

      sendMessages(pSettings, log, env)

      Get(s"/activemq/Queue1") ~> route ~> check {
        val r = response
        r.status should be(StatusCodes.OK)
        r.entity.contentType should be(ContentTypes.`application/octet-stream`)

        val g = r.entity.dataBytes.toMat(Sink.seq)(Keep.right)
        val vf = g.run()

        val v : ByteString = Await.result(vf, 1.second).foldLeft(ByteString("")) { case (c, e) => c.concat(e) }
        v should be(ByteString(msg))

      }
    }
  }

}
