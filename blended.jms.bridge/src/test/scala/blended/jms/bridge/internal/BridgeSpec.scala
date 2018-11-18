package blended.jms.bridge.internal

import java.io.File

import akka.actor.ActorSystem
import akka.stream._
import blended.activemq.brokerstarter.internal.BrokerActivator
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
import org.scalacheck.Gen
import org.scalatest.Matchers
import org.scalatest.prop.PropertyChecks

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext}

@RequiresForkedJVM
class BridgeSpec extends SimplePojoContainerSpec
  with LoggingFreeSpecLike
  with PojoSrTestHelper
  with Matchers
  with JmsStreamSupport
  with PropertyChecks {

  private val log = Logger[BridgeSpec]

  override def baseDir: String = new File(BlendedTestSupport.projectTestOutput, "container").getAbsolutePath()

  override def bundles: Seq[(String, BundleActivator)] = Seq(
    "blended.akka" -> new BlendedAkkaActivator(),
    "blended.activemq.brokerstarter" -> new BrokerActivator(),
    "blended.jms.bridge" -> new BridgeActivator()
  )

  implicit val timeout = 5.seconds

  private implicit val system : ActorSystem = mandatoryService[ActorSystem](registry)(None)
  private implicit val materializer : ActorMaterializer = ActorMaterializer()
  private implicit val ectxt : ExecutionContext = system.dispatcher

  private val (internal, external) = getConnectionFactories(registry)
  private val idSvc = mandatoryService[ContainerIdentifierService](registry)(None)

  private val headerCfg : FlowHeaderConfig =
    FlowHeaderConfig.create(idSvc.containerContext.getContainerConfig().getConfig("blended.flow.header"))

  private val destHeader = new JmsEnvelopeHeader(){}.destHeader(headerCfg.prefix)

  val ctrlCfg : BridgeControllerConfig = BridgeControllerConfig.create(
    cfg = idSvc.containerContext.getContainerConfig().getConfig("blended.jms.bridge"),
    internalCf = internal,
    idSvc = idSvc
  )

  val cfg : JmsStreamConfig = JmsStreamConfig(
    inbound = true,
    headerCfg = ctrlCfg.headerCfg,
    fromCf = internal,
    fromDest = JmsDestination.create(s"bridge.data.in.${external.vendor}.${external.provider}").get,
    toCf = internal,
    toDest = Some(JmsDestination.create(s"bridge.data.out.${external.vendor}.${external.provider}").get),
    listener = 3,
    selector = None,
    registry = ctrlCfg.registry,
    trackTransAction = TrackTransaction.Off,
    subscriberName = None,
    header = List.empty
  )

  private val streamCfg = new JmsStreamBuilder(cfg).streamCfg
  system.actorOf(StreamController.props(streamCfg))

  private def brokerFilter(provider : String) : String = s"(&(vendor=activemq)(provider=$provider))"

  private def getConnectionFactories(sr: BlendedPojoRegistry)(implicit timeout : FiniteDuration) : (IdAwareConnectionFactory, IdAwareConnectionFactory) = {
    val cf1 = mandatoryService[IdAwareConnectionFactory](sr)(Some(brokerFilter("internal")))
    val cf2 = mandatoryService[IdAwareConnectionFactory](sr)(Some(brokerFilter("external")))
    (cf1, cf2)
  }

  "The bridge activator should" - {

    "process normal in- and outbound messages" in {

      val msgCount = 2

      val msgs : Seq[FlowEnvelope] = 1.to(msgCount).map { i =>
        FlowMessage(s"Message $i")(FlowMessage.noProps)
          .withHeader(destHeader, s"sampleOut.$i").get
          .withHeader(headerCfg.headerTrack, true).get
      } map { FlowEnvelope.apply }

      val switch = sendMessages(external, JmsQueue("sampleIn"), log, msgs:_*)

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

    "process messages with optional header configs" in {

      forAll { (desc : String) =>
        whenever(desc.nonEmpty) {
          val env : FlowEnvelope = FlowEnvelope(FlowMessage("Header")(FlowMessage.props(
            destHeader -> "SampleHeaderOut",
            "Description" -> desc,
            headerCfg.headerTrack -> false
          ).get))

          val switch = sendMessages(external, JmsQueue("SampleHeaderIn"), log, env)
          val coll = receiveMessages(ctrlCfg.headerCfg, external, JmsQueue("SampleHeaderOut"))(1.second, system, materializer)
          val result = Await.result(coll.result, 1100.millis)
          switch.shutdown()

          result should have size 1
          result.head.header[String]("ResourceType") should be (Some(desc))
        }
      }
    }
  }

}
