package blended.jms.bridge.internal

import java.io.File

import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, Materializer}
import akka.stream.scaladsl.{Flow, Keep, Sink, Source}
import blended.activemq.brokerstarter.BrokerActivator
import blended.akka.internal.BlendedAkkaActivator
import blended.jms.utils.{IdAwareConnectionFactory, JmsDestination, JmsQueue}
import blended.streams.jms.{JmsProducerSettings, JmsSinkStage}
import blended.streams.message.{DefaultFlowEnvelope, FlowMessage, MsgProperty}
import blended.testsupport.BlendedTestSupport
import blended.testsupport.pojosr.{PojoSrTestHelper, SimplePojosrBlendedContainer}
import blended.testsupport.scalatest.LoggingFreeSpec
import blended.util.logging.Logger
import org.scalatest.Matchers

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.util.{Failure, Success}


class BridgeActivatorSpec extends LoggingFreeSpec
  with SimplePojosrBlendedContainer
  with PojoSrTestHelper
  with Matchers {

  private val log = Logger[BridgeActivatorSpec]
  private val baseDir = new File(BlendedTestSupport.projectTestOutput, "container").getAbsolutePath()

    "The bridge activator should" - {

      def sendTestMessages(
        cf: IdAwareConnectionFactory,
        dest: JmsDestination
      )(implicit system: ActorSystem, materializer: Materializer, ectxt: ExecutionContext): Unit = {

        val msgs = 1.to(10).map { i =>
          val header: Map[String, MsgProperty[_]] = Map("foo" -> "bar", "msgno" -> i)
          FlowMessage(s"Message $i", header)
        }

        val settings: JmsProducerSettings = JmsProducerSettings(
          connectionFactory = cf,
          connectionTimeout = 1.second,
          jmsDestination = Some(dest)
        )

        val sink = Flow[FlowMessage]
          .map(m => DefaultFlowEnvelope(m))
          .viaMat(Flow.fromGraph(new JmsSinkStage(settings)))(Keep.left)

        val foo = Source
          .fromIterator(() => msgs.toIterator)
          .viaMat(sink)(Keep.right)
          .watchTermination()(Keep.right)
          .toMat(Sink.ignore)(Keep.both)

        foo.run()._1.onComplete {
          case Success(msgs) => log.info(s"Processed all messages.")
          case Failure(t) => log.error(t)("Encountered exception")
        }
      }

      "start the bridge correctly" in {

        withSimpleBlendedContainer(baseDir) { sr =>
          withStartedBundles(sr)(Seq(
            "blended.akka" -> Some(() => new BlendedAkkaActivator()),
            "blended.activemq.brokerstarter" -> Some(() => new BrokerActivator()),
            "blended.jms.bridge" -> Some(() => new BridgeActivator())
          )
          ) { sr =>

            implicit val timeout = 10.seconds

            waitOnService[ActorSystem](sr)() match {
              case None => fail("No ActorSystem service")
              case Some(s) =>
                implicit val system = s
                implicit val materializer = ActorMaterializer()
                implicit val ectxt = system.dispatcher

                (
                  waitOnService[IdAwareConnectionFactory](sr)(Some("(&(vendor=activemq)(provider=blended))")),
                  waitOnService[IdAwareConnectionFactory](sr)(Some("(&(vendor=activemq)(provider=broker2))"))
                ) match {
                  case (Some(cf1), Some(cf2)) =>
                    sendTestMessages(cf1, JmsQueue("sampleIn"))
                    Thread.sleep(10000)

                  case _ => fail("Missing one connection factory")
                }

            }
          }
        }
      }

  }
}

