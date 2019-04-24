package blended.jms.bridge.internal

import java.io.File

import akka.actor.ActorSystem
import akka.stream._
import blended.activemq.brokerstarter.internal.BrokerActivator
import blended.akka.internal.BlendedAkkaActivator
import blended.container.context.api.ContainerIdentifierService
import blended.jms.utils.{IdAwareConnectionFactory, JmsDestination, JmsQueue}
import blended.streams.jms._
import blended.streams.message.{FlowEnvelope, FlowMessage}
import blended.streams.processor.Collector
import blended.streams.transaction.{FlowHeaderConfig, FlowTransactionEvent, FlowTransactionStarted, FlowTransactionUpdate}
import blended.testsupport.pojosr.{BlendedPojoRegistry, PojoSrTestHelper, SimplePojoContainerSpec}
import blended.testsupport.scalatest.LoggingFreeSpecLike
import blended.testsupport.{BlendedTestSupport, RequiresForkedJVM}
import blended.util.logging.Logger
import org.osgi.framework.BundleActivator
import org.scalatest.Matchers
import org.scalatest.prop.PropertyChecks

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext}
import scala.util.Try

abstract class BridgeSpecSupport extends SimplePojoContainerSpec
  with LoggingFreeSpecLike
  with PojoSrTestHelper
  with Matchers
  with JmsStreamSupport
  with JmsEnvelopeHeader
  with PropertyChecks {

  protected implicit val timeout : FiniteDuration = 5.seconds
  protected val log = Logger[InboundBridgeSpec]

  override def baseDir: String = new File(BlendedTestSupport.projectTestOutput, "container").getAbsolutePath()

  override def bundles: Seq[(String, BundleActivator)] = Seq(
    "blended.akka" -> new BlendedAkkaActivator(),
    "blended.activemq.brokerstarter" -> new BrokerActivator(),
    "blended.jms.bridge" -> new BridgeActivator()
  )

  protected implicit val system : ActorSystem = mandatoryService[ActorSystem](registry)(None)
  protected implicit val materializer : ActorMaterializer = ActorMaterializer()
  protected implicit val ectxt : ExecutionContext = system.dispatcher

  protected val (internal, external) = getConnectionFactories(registry)
  protected val idSvc = mandatoryService[ContainerIdentifierService](registry)(None)

  protected val headerCfg : FlowHeaderConfig = FlowHeaderConfig.create(idSvc)

  protected val destinationName = destHeader(headerCfg.prefix)

  val ctrlCfg : BridgeControllerConfig = BridgeControllerConfig.create(
    cfg = idSvc.containerContext.getContainerConfig().getConfig("blended.jms.bridge"),
    internalCf = internal,
    idSvc = idSvc
  )

  protected def brokerFilter(provider : String) : String = s"(&(vendor=activemq)(provider=$provider))"

  protected def getConnectionFactories(sr: BlendedPojoRegistry)(implicit timeout : FiniteDuration) : (IdAwareConnectionFactory, IdAwareConnectionFactory) = {
    val cf1 = mandatoryService[IdAwareConnectionFactory](sr)(Some(brokerFilter("internal")))
    val cf2 = mandatoryService[IdAwareConnectionFactory](sr)(Some(brokerFilter("external")))
    (cf1, cf2)
  }

  protected def consumeMessages(cf: IdAwareConnectionFactory, destName : String)(
    implicit timeout : FiniteDuration, system : ActorSystem, materializer: Materializer
  ) : Try[List[FlowEnvelope]] = Try {

    val coll : Collector[FlowEnvelope] = receiveMessages(ctrlCfg.headerCfg, cf, JmsDestination.create(destName).get, log)
    Await.result(coll.result, timeout + 100.millis)
  }

  protected def consumeEvents()(implicit timeout : FiniteDuration, system : ActorSystem, materializer: Materializer) : Try[List[FlowTransactionEvent]] = Try {
    consumeMessages(internal, "internal.transactions").get.map{ env =>
      FlowTransactionEvent.envelope2event(ctrlCfg.headerCfg)(env).get
    }
  }

  protected def generateMessages(msgCount : Int)(f: FlowEnvelope => FlowEnvelope = { env => env} ) : Try[Seq[FlowEnvelope]] = Try {

    1.to(msgCount).map { i =>
      FlowMessage(s"Message $i")(FlowMessage.noProps)
        .withHeader("UnitProperty", null).get
    }.map(FlowEnvelope.apply).map(f)
  }

  protected def sendMessages(destName : String, cf : IdAwareConnectionFactory)(msgs : FlowEnvelope*) : KillSwitch = {
    val pSettings : JmsProducerSettings = JmsProducerSettings(
      log = log,
      connectionFactory = cf,
      jmsDestination = Some(JmsDestination.create(destName).get)
    )

    sendMessages(pSettings, log, msgs:_*).get
  }
}

@RequiresForkedJVM
class InboundBridgeSpec extends BridgeSpecSupport {

  private def sendInbound(msgCount : Int, track : Boolean) : KillSwitch = {
    val msgs : Seq[FlowEnvelope] = generateMessages(msgCount){ env =>
      env
        .withHeader(destinationName, s"sampleOut").get
        .withHeader(headerCfg.headerTrack, track).get
    }.get


    sendMessages("sampleIn", external)(msgs:_*)
  }

  "The Inbound Bridge should" - {

    // We only test for tracked transactions as all bridge inbound streams generate transaction started events by design
    "process normal inbound messages with tracked transactions" in {
      implicit val timeout : FiniteDuration = 1.second
      val msgCount = 2

      val switch = sendInbound(msgCount, true)

      val messages : List[FlowEnvelope] =
        consumeMessages(internal, "bridge.data.in.activemq.external")(1.second, system, materializer).get

      messages should have size(msgCount)

      messages.foreach{ env =>
        env.header[Unit]("UnitProperty") should be (Some(()))
      }

      val envelopes : List[FlowTransactionEvent] = consumeEvents().get

      envelopes should have size(msgCount)
      assert(envelopes.forall(_.isInstanceOf[FlowTransactionStarted]))

      switch.shutdown()
    }

    "process messages with optional header configs" in {

      val desc = "TestDesc"

      val env : FlowEnvelope = FlowEnvelope(FlowMessage("Header")(FlowMessage.props(
        destinationName -> "SampleHeaderOut",
        "Description" -> desc,
        headerCfg.headerTrack -> false
      ).get))

      val pSettings : JmsProducerSettings = JmsProducerSettings(
        log = log,
        connectionFactory = external,
        jmsDestination = Some(JmsQueue("SampleHeaderIn"))
      )

      val switch : KillSwitch = sendMessages(pSettings, log, env).get

      val result : List[FlowEnvelope] = consumeMessages(internal, "bridge.data.in.activemq.external").get

      result should have size 1
      result.head.header[String]("ResourceType") should be (Some(desc))

      switch.shutdown()
    }
  }
}

@RequiresForkedJVM
class OutboundBridgeSpec extends BridgeSpecSupport {

  private def sendOutbound(msgCount : Int, track : Boolean) : KillSwitch = {
    val msgs : Seq[FlowEnvelope] = generateMessages(msgCount){ env =>
      env
        .withHeader(destinationName, s"sampleOut").get
        .withHeader(headerCfg.headerTrack, track).get
    }.get


    sendMessages("bridge.data.out.activemq.external", internal)(msgs:_*)
  }

  "The outbound bridge should " - {

    "process normal inbound messages with untracked transactions" in {
      implicit val timeout : FiniteDuration = 1.second
      val msgCount = 2

      val switch = sendOutbound(msgCount, false)

      val messages : List[FlowEnvelope] =
        consumeMessages(external, "sampleOut").get

      messages should have size(msgCount)

      messages.foreach{ env =>
        env.header[Unit]("UnitProperty") should be (Some(()))
      }

      val envelopes : List[FlowTransactionEvent] = consumeEvents().get

      envelopes should be (empty)
    }

    "process normal inbound messages with tracked transactions" in {
      implicit val timeout : FiniteDuration = 1.second
      val msgCount = 2

      val switch = sendOutbound(msgCount, true)

      val messages : List[FlowEnvelope] =
        consumeMessages(external, "sampleOut").get

      messages should have size(msgCount)

      messages.foreach{ env =>
        env.header[Unit]("UnitProperty") should be (Some(()))
      }

      val envelopes : List[FlowTransactionEvent] = consumeEvents().get

      envelopes should have size(msgCount)
      assert(envelopes.forall(_.isInstanceOf[FlowTransactionUpdate]))
    }
  }
}
