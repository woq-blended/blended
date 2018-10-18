package blended.jms.bridge.internal

import java.io.File

import akka.{Done, NotUsed}
import akka.actor.{ActorRef, ActorSystem}
import akka.stream.scaladsl.{Flow, Keep, Sink, Source}
import akka.stream._
import blended.activemq.brokerstarter.BrokerActivator
import blended.akka.internal.BlendedAkkaActivator
import blended.jms.bridge.{JmsProducerSupport, RestartableJmsSource}
import blended.jms.utils.{IdAwareConnectionFactory, JmsDestination, JmsQueue}
import blended.streams.jms._
import blended.streams.message.MsgProperty.Implicits._
import blended.streams.message.{FlowEnvelope, FlowMessage, MsgProperty}
import blended.streams.testsupport.StreamFactories
import blended.testsupport.BlendedTestSupport
import blended.testsupport.pojosr.{PojoSrTestHelper, SimplePojosrBlendedContainer}
import blended.testsupport.scalatest.LoggingFreeSpec
import blended.util.logging.Logger
import org.scalatest.Matchers

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._
import scala.util.{Failure, Success}


class BridgeActivatorSpec extends LoggingFreeSpec
  with SimplePojosrBlendedContainer
  with PojoSrTestHelper
  with Matchers {

  private val log = Logger[BridgeActivatorSpec]
  private val baseDir = new File(BlendedTestSupport.projectTestOutput, "container").getAbsolutePath()

    "The bridge activator should" - {

      def sendMessages(
        cf: IdAwareConnectionFactory,
        dest: JmsDestination,
        count: Int
      )(implicit system: ActorSystem, materializer: Materializer, ectxt: ExecutionContext): KillSwitch = {

        val msgs = 1.to(count).map { i =>
          val header: Map[String, MsgProperty[_]] = Map("foo" -> "bar", "msgno" -> i)
          FlowMessage(s"Message $i", header)
        } map(FlowEnvelope.apply)

        val settings: JmsProducerSettings = JmsProducerSettings(
          connectionFactory = cf,
          connectionTimeout = 1.second,
          jmsDestination = Some(dest)
        )

        val toJms = JmsProducerSupport.jmsProducer(
          name = "sender",
          settings = settings,
          autoAck = true,
          log = None
        )

        StreamFactories.sendAndKeepAlive(toJms, msgs:_*)
      }

      "start the bridge correctly" in {

        withSimpleBlendedContainer(baseDir) { sr =>
          withStartedBundles(sr)(Seq(
            "blended.akka" -> Some(() => new BlendedAkkaActivator()),
            "blended.activemq.brokerstarter" -> Some(() => new BrokerActivator()),
            "blended.jms.bridge" -> Some(() => new BridgeActivator())
          )
          ) { sr =>

            val msgCount = 50
            implicit val timeout = 10.seconds

            waitOnService[ActorSystem](sr)() match {
              case None => fail("No ActorSystem service")
              case Some(s) =>
                implicit val system = s
                implicit val materializer = ActorMaterializer()
                implicit val ectxt = system.dispatcher

                val cf1 = mandatoryService[IdAwareConnectionFactory](sr)(Some("(&(vendor=activemq)(provider=internal))"))
                val cf2 = mandatoryService[IdAwareConnectionFactory](sr)(Some("(&(vendor=activemq)(provider=external))"))

                val switch = sendMessages(cf2, JmsQueue("sampleIn"), msgCount)

                val messages = StreamFactories.runSourceWithTimeLimit(
                  "received",
                  RestartableJmsSource(
                    name = "receiver",
                    settings = JMSConsumerSettings(cf1).withSessionCount(5).withDestination(Some(JmsQueue("bridge.data.in"))),
                    requiresAck = false
                  ),
                  5.seconds
                )

                messages should have size(msgCount)
                switch.shutdown()
            }
          }
        }
      }

  }
}

