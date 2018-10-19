package blended.jms.bridge.internal

import java.io.File

import akka.actor.ActorSystem
import akka.stream._
import blended.activemq.brokerstarter.BrokerActivator
import blended.akka.internal.BlendedAkkaActivator
import blended.container.context.api.ContainerIdentifierService
import blended.jms.bridge.{JmsProducerSupport, RestartableJmsSource}
import blended.jms.utils.{IdAwareConnectionFactory, JmsDestination, JmsQueue}
import blended.streams.StreamController
import blended.streams.jms._
import blended.streams.message.MsgProperty.Implicits._
import blended.streams.message.{FlowEnvelope, FlowMessage, MsgProperty}
import blended.streams.testsupport.StreamFactories
import blended.testsupport.BlendedTestSupport
import blended.testsupport.pojosr.{BlendedPojoRegistry, PojoSrTestHelper, SimplePojosrBlendedContainer}
import blended.testsupport.scalatest.LoggingFreeSpec
import blended.util.logging.Logger
import org.scalatest.Matchers

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._


class BridgeSpec extends LoggingFreeSpec
  with SimplePojosrBlendedContainer
  with PojoSrTestHelper
  with Matchers {

  private val log = Logger[BridgeSpec]
  private val baseDir = new File(BlendedTestSupport.projectTestOutput, "container").getAbsolutePath()

    "The bridge activator should" - {

      def sendMessages(
        cf: IdAwareConnectionFactory,
        dest: JmsDestination,
        msgs : FlowEnvelope*
      )(implicit system: ActorSystem, materializer: Materializer, ectxt: ExecutionContext): KillSwitch = {

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

      def receiveMessages(cf : IdAwareConnectionFactory, dest : JmsDestination)(implicit timeout : FiniteDuration, system: ActorSystem, materializer: ActorMaterializer):List[FlowEnvelope] = {

        StreamFactories.runSourceWithTimeLimit(
          "received",
          RestartableJmsSource(
            name = "receiver",
            settings = JMSConsumerSettings(cf).withSessionCount(2).withDestination(Some(dest)),
            requiresAck = false
          ),
          timeout
        )
      }

      def withStartedBridge[T](t : FiniteDuration)(f : ActorSystem => BlendedPojoRegistry => T):T = {
        withSimpleBlendedContainer(baseDir) { sr =>
          withStartedBundles(sr)(Seq(
            "blended.akka" -> Some(() => new BlendedAkkaActivator()),
            "blended.activemq.brokerstarter" -> Some(() => new BrokerActivator()),
            "blended.jms.bridge" -> Some(() => new BridgeActivator())
          )
          ) { sr =>
            implicit val timeout : FiniteDuration = t
            val system : ActorSystem = mandatoryService[ActorSystem](sr)(None)

            f(system)(sr)
          }
        }
      }

      def getConnectionFactories(sr: BlendedPojoRegistry)(implicit timeout : FiniteDuration) : (IdAwareConnectionFactory, IdAwareConnectionFactory) = {
        val cf1 = mandatoryService[IdAwareConnectionFactory](sr)(Some("(&(vendor=activemq)(provider=internal))"))
        val cf2 = mandatoryService[IdAwareConnectionFactory](sr)(Some("(&(vendor=activemq)(provider=external))"))
        (cf1, cf2)
      }

//      "process inbound messages" in {
//
//        implicit val timeout = 3.seconds
//
//        withStartedBridge(timeout) { s => sr =>
//
//          val msgCount = 5
//
//          val msgs = 1.to(msgCount).map { i =>
//            val header: Map[String, MsgProperty[_]] = Map("foo" -> "bar", "msgno" -> i)
//            FlowMessage(s"Message $i", header)
//          } map(FlowEnvelope.apply)
//
//          implicit val system : ActorSystem = s
//          implicit val materializer : ActorMaterializer = ActorMaterializer()
//          implicit val ectxt : ExecutionContext = system.dispatcher
//
//          val (internal, external) = getConnectionFactories(sr)
//          val switch = sendMessages(external, JmsQueue("sampleIn"), msgs:_*)
//          val messages = receiveMessages(internal, JmsQueue(s"bridge.data.in.${external.vendor}.${external.provider}"))
//
//          messages should have size (msgCount)
//          switch.shutdown()
//        }
//      }

      "process outbound messages" in {

        implicit val timeout = 5.seconds

        withStartedBridge(timeout) { s => sr =>

          implicit val system : ActorSystem = s
          implicit val materializer : ActorMaterializer = ActorMaterializer()
          implicit val ectxt : ExecutionContext = system.dispatcher

          val (internal, external) = getConnectionFactories(sr)
          val idSvc = mandatoryService[ContainerIdentifierService](sr)(None)

          val ctrlCfg = BridgeControllerConfig.create(
            cfg = idSvc.containerContext.getContainerConfig().getConfig("blended.jms.bridge"),
            internalCf = internal,
            idSvc = idSvc
          )

          val msgCount = 5

          val msgs = 1.to(msgCount).map { i =>
            FlowMessage(s"Message $i", FlowMessage.noProps)
              .withHeader("BlendedJMSDestination", "sampleOut").get
          } map { FlowEnvelope.apply }

          val streamCfg = BridgeController.bridgeStream(
            ctrlCfg = ctrlCfg,
            fromCf = internal,
            fromDest = JmsDestination.create(s"bridge.data.in.${external.vendor}.${external.provider}").get,
            toCf = internal,
            toDest = Some(JmsDestination.create(s"bridge.data.out.${external.vendor}.${external.provider}").get),
            listener = 3,
            selector = None
          )

          system.actorOf(StreamController.props(streamCfg))

          val switch = sendMessages(external, JmsQueue("sampleIn"), msgs:_*)
          val messages = receiveMessages(external, JmsQueue("sampleOut"))(timeout, system, materializer)

          messages should have size (msgCount)
          switch.shutdown()
        }
      }
  }
}

