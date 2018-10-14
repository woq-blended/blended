package blended.jms.bridge.internal

import java.io.File

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.scaladsl.{Flow, Keep, RestartSource, Sink, Source}
import akka.stream.{ActorMaterializer, Materializer}
import blended.activemq.brokerstarter.BrokerActivator
import blended.akka.internal.BlendedAkkaActivator
import blended.jms.utils.{IdAwareConnectionFactory, JmsDestination, JmsQueue}
import blended.streams.jms._
import blended.streams.message.{FlowEnvelope, FlowMessage, MsgProperty}
import blended.streams.processor.AckProcessor
import blended.testsupport.BlendedTestSupport
import blended.testsupport.pojosr.{PojoSrTestHelper, SimplePojosrBlendedContainer}
import blended.testsupport.scalatest.LoggingFreeSpec
import blended.util.logging.Logger
import org.scalatest.Matchers

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext}
import scala.util.{Failure, Success}


class BridgeActivatorSpec extends LoggingFreeSpec
  with SimplePojosrBlendedContainer
  with PojoSrTestHelper
  with Matchers {

  private val log = Logger[BridgeActivatorSpec]
  private val baseDir = new File(BlendedTestSupport.projectTestOutput, "container").getAbsolutePath()

    "The bridge activator should" - {

      def consume(
        cf: IdAwareConnectionFactory,
        withAck: Boolean,
        dest: JmsDestination,
        timeout: FiniteDuration
      )(implicit system: ActorSystem, materializer: Materializer, ectxt: ExecutionContext) : Source[FlowEnvelope, NotUsed] = {

        val cSettings = JMSConsumerSettings.create(cf).withDestination(Some(dest)).withSessionCount(5)

        val innerSource : Source[FlowEnvelope, NotUsed]= if (withAck) {
          Source.fromGraph(new JmsAckSourceStage(cSettings, system))
        } else {
          Source.fromGraph(new JmsSourceStage(cSettings, system))
        }

        val source : Source[FlowEnvelope, NotUsed] = RestartSource.onFailuresWithBackoff(
          minBackoff = 2.seconds,
          maxBackoff = 10.seconds,
          randomFactor = 0.2,
          maxRestarts = 10,
        ) { () => innerSource }

        source.via(AckProcessor("testAck").flow)
      }


      def sendMessages(
        cf: IdAwareConnectionFactory,
        dest: JmsDestination,
        count: Int
      )(implicit system: ActorSystem, materializer: Materializer, ectxt: ExecutionContext): Unit = {

        val msgs = 1.to(count).map { i =>
          val header: Map[String, MsgProperty[_]] = Map("foo" -> "bar", "msgno" -> i)
          FlowMessage(s"Message $i", header)
        }

        val settings: JmsProducerSettings = JmsProducerSettings(
          connectionFactory = cf,
          connectionTimeout = 1.second,
          jmsDestination = Some(dest)
        )

        val sink = Flow[FlowMessage]
          .map(m => FlowEnvelope(m))
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
                  waitOnService[IdAwareConnectionFactory](sr)(Some("(&(vendor=activemq)(provider=internal))")),
                  waitOnService[IdAwareConnectionFactory](sr)(Some("(&(vendor=activemq)(provider=external))"))
                ) match {
                  case (Some(cf1), Some(cf2)) =>
                    sendMessages(cf2, JmsQueue("sampleIn"), 10)
                    val received = consume(cf1, false, JmsQueue("bridge.data.in"), 10.seconds).take(10).runWith(Sink.seq)
                    Await.result(received, 5.seconds) should have size(10)

                  case _ => fail("Missing at least one connection factory")
                }

            }
          }
        }
      }

  }
}

