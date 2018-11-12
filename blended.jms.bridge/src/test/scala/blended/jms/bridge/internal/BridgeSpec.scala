package blended.jms.bridge.internal

import java.io.File

import akka.actor.ActorSystem
import akka.stream._
import blended.activemq.brokerstarter.BrokerActivator
import blended.akka.internal.BlendedAkkaActivator
import blended.container.context.api.ContainerIdentifierService
import blended.jms.bridge.{JmsStreamBuilder, JmsStreamConfig, TrackTransaction}
import blended.jms.utils.{IdAwareConnectionFactory, JmsDestination, JmsQueue}
import blended.streams.StreamController
import blended.streams.jms._
import blended.streams.message.{FlowEnvelope, FlowMessage}
import blended.streams.transaction.{FlowHeaderConfig, FlowTransactionEvent, FlowTransactionStarted, FlowTransactionUpdate}
import blended.testsupport.{BlendedTestSupport, RequiresForkedJVM}
import blended.testsupport.pojosr.{BlendedPojoRegistry, PojoSrTestHelper, SimplePojoContainerSpec}
import blended.testsupport.scalatest.LoggingFreeSpecLike
import blended.util.logging.Logger
import org.osgi.framework.BundleActivator
import org.scalatest.Matchers

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext}

@RequiresForkedJVM
class BridgeSpec extends SimplePojoContainerSpec
  with LoggingFreeSpecLike
  with PojoSrTestHelper
  with Matchers
  with JmsStreamSupport {

  private val log = Logger[BridgeSpec]

  override def baseDir: String = new File(BlendedTestSupport.projectTestOutput, "container").getAbsolutePath()

  override def bundles: Seq[(String, BundleActivator)] = Seq(
    "blended.akka" -> new BlendedAkkaActivator(),
    "blended.activemq.brokerstarter" -> new BrokerActivator(),
    "blended.jms.bridge" -> new BridgeActivator()
  )

  private def brokerFilter(provider : String) : String = s"(&(vendor=activemq)(provider=$provider))"

  private def withStartedBridge[T](t : FiniteDuration)(f : ActorSystem => BlendedPojoRegistry => Unit) : Unit= {
    implicit val timeout : FiniteDuration = t
    val system : ActorSystem = mandatoryService[ActorSystem](registry)(None)

    f(system)(registry)
  }

  private def getConnectionFactories(sr: BlendedPojoRegistry)(implicit timeout : FiniteDuration) : (IdAwareConnectionFactory, IdAwareConnectionFactory) = {
    val cf1 = mandatoryService[IdAwareConnectionFactory](sr)(Some(brokerFilter("internal")))
    val cf2 = mandatoryService[IdAwareConnectionFactory](sr)(Some(brokerFilter("external")))
    (cf1, cf2)
  }

  "The bridge activator should" - {

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
            .withHeader(headerCfg.prefix + headerCfg.headerTrack, true).get
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
          trackTransAction = TrackTransaction.Off
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
