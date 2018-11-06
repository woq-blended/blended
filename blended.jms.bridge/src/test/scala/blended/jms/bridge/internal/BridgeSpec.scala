package blended.jms.bridge.internal

import java.io.File

import akka.actor.ActorSystem
import akka.stream._
import blended.activemq.brokerstarter.BrokerActivator
import blended.akka.internal.BlendedAkkaActivator
import blended.container.context.api.ContainerIdentifierService
import blended.jms.bridge.{JmsStreamBuilder, JmsStreamConfig}
import blended.jms.utils.{IdAwareConnectionFactory, JmsDestination, JmsQueue}
import blended.streams.StreamController
import blended.streams.jms._
import blended.streams.message.{FlowEnvelope, FlowMessage}
import blended.streams.transaction.{FlowHeaderConfig, FlowTransactionEvent, FlowTransactionStarted, FlowTransactionUpdate}
import blended.testsupport.BlendedTestSupport
import blended.testsupport.pojosr.{BlendedPojoRegistry, PojoSrTestHelper, SimplePojosrBlendedContainer}
import blended.testsupport.scalatest.LoggingFreeSpec
import blended.util.logging.Logger
import org.scalatest.Matchers

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext}


class BridgeSpec extends LoggingFreeSpec
  with SimplePojosrBlendedContainer
  with PojoSrTestHelper
  with Matchers
  with JmsStreamSupport {

  private val log = Logger[BridgeSpec]
  private val baseDir = new File(BlendedTestSupport.projectTestOutput, "container").getAbsolutePath()

    "The bridge activator should" - {

      def brokerFilter(provider : String) : String = s"(&(vendor=activemq)(provider=$provider))"
      
      // Convenience method to execute the bridge
      def withStartedBridge[T](t : FiniteDuration)(f : ActorSystem => BlendedPojoRegistry => Unit) : Unit= {
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
        val cf1 = mandatoryService[IdAwareConnectionFactory](sr)(Some(brokerFilter("internal")))
        val cf2 = mandatoryService[IdAwareConnectionFactory](sr)(Some(brokerFilter("external")))
        (cf1, cf2)
      }

      "process in- and outbound messages" in {

        implicit val timeout = 5.seconds

        withStartedBridge(timeout) { s => sr =>

          implicit val system : ActorSystem = s
          implicit val materializer : ActorMaterializer = ActorMaterializer()
          implicit val ectxt : ExecutionContext = system.dispatcher

          val (internal, external) = getConnectionFactories(sr)
          val idSvc = mandatoryService[ContainerIdentifierService](sr)(None)

          val headerCfg : FlowHeaderConfig =
            FlowHeaderConfig.create(idSvc.containerContext.getContainerConfig().getConfig("blended.flow.header"))

          val ctrlCfg : BridgeControllerConfig = BridgeControllerConfig.create(
            cfg = idSvc.containerContext.getContainerConfig().getConfig("blended.jms.bridge"),
            internalCf = internal,
            idSvc = idSvc
          )

          val msgCount = 2

          val destHeader = new JmsEnvelopeHeader(){}.destHeader(headerCfg.prefix)

          val msgs = 1.to(msgCount).map { i =>
            FlowMessage(s"Message $i", FlowMessage.noProps)
              .withHeader(destHeader, s"sampleOut.$i").get
          } map { FlowEnvelope.apply }

          val cfg : JmsStreamConfig = JmsStreamConfig(
            headerCfg = ctrlCfg.headerCfg,
            fromCf = internal,
            fromDest = JmsDestination.create(s"bridge.data.in.${external.vendor}.${external.provider}").get,
            toCf = internal,
            toDest = Some(JmsDestination.create(s"bridge.data.out.${external.vendor}.${external.provider}").get),
            listener = 3,
            selector = None,
            registry = ctrlCfg.registry,
            trackTransAction = false
          )

          val streamCfg = new JmsStreamBuilder(cfg).streamCfg

          system.actorOf(StreamController.props(streamCfg))

          val switch = sendMessages(ctrlCfg.headerCfg, external, JmsQueue("sampleIn"), log, msgs:_*)

          1.to(msgCount).map { i =>
            val messages = receiveMessages(ctrlCfg.headerCfg, external, JmsQueue(s"sampleOut.$i"))(1.second, system, materializer)
            messages.result.map { l =>
              l should have size(1)
            }
          }

          val collector = receiveMessages(ctrlCfg.headerCfg, internal, JmsQueue("internal.transactions"))(1.second, system, materializer)

          val result = collector.result.map { l =>
            val envelopes = l.map(env => FlowTransactionEvent.envelope2event(ctrlCfg.headerCfg)(env).get)

            envelopes should have size(msgCount * 2)
            val (started, updated) = envelopes.partition(_.isInstanceOf[FlowTransactionStarted])

            started should have size msgCount
            updated should have size msgCount

            assert(updated.forall(_.isInstanceOf[FlowTransactionUpdate]))

            val sIds = started.map(_.transactionId)
            val uIds = updated.map(_.transactionId)

            assert(sIds.forall(id => uIds.contains(id)))
          }

          Await.result(result, 3.seconds)
          switch.shutdown()
        }
      }
  }
}
