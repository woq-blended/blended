package blended.jms.bridge.internal

import java.io.File

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream._
import akka.stream.scaladsl.Flow
import blended.activemq.brokerstarter.internal.BrokerActivator
import blended.akka.internal.BlendedAkkaActivator
import blended.container.context.api.ContainerIdentifierService
import blended.jms.utils.{IdAwareConnectionFactory, JmsDestination, JmsQueue}
import blended.streams.jms._
import blended.streams.message.{BinaryFlowMessage, FlowEnvelope, FlowMessage, TextFlowMessage}
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
  protected val log = Logger(getClass().getName())

  override def baseDir : String = new File(BlendedTestSupport.projectTestOutput, "withRetries").getAbsolutePath()

  protected def bridgeActivator : BridgeActivator = new BridgeActivator()

  override def bundles : Seq[(String, BundleActivator)] = Seq(
    "blended.akka" -> new BlendedAkkaActivator(),
    "blended.activemq.brokerstarter" -> new BrokerActivator(),
    "blended.jms.bridge" -> bridgeActivator
  )

  protected implicit val system : ActorSystem = mandatoryService[ActorSystem](registry)(None)
  protected implicit val materializer : ActorMaterializer = ActorMaterializer()
  protected implicit val ectxt : ExecutionContext = system.dispatcher

  protected val (internal, external) = getConnectionFactories(registry)
  protected val idSvc : ContainerIdentifierService = mandatoryService[ContainerIdentifierService](registry)(None)

  protected val headerCfg : FlowHeaderConfig = FlowHeaderConfig.create(idSvc)

  protected val destinationName : String = destHeader(headerCfg.prefix)

  protected def brokerFilter(provider : String) : String = s"(&(vendor=activemq)(provider=$provider))"

  protected def getConnectionFactories(sr : BlendedPojoRegistry)(implicit timeout : FiniteDuration) : (IdAwareConnectionFactory, IdAwareConnectionFactory) = {
    val cf1 = mandatoryService[IdAwareConnectionFactory](sr)(Some(brokerFilter("internal")))
    val cf2 = mandatoryService[IdAwareConnectionFactory](sr)(Some(brokerFilter("external")))
    (cf1, cf2)
  }

  protected def consumeMessages(cf : IdAwareConnectionFactory, destName : String)(
    implicit
    timeout : FiniteDuration, system : ActorSystem, materializer : Materializer
  ) : Try[List[FlowEnvelope]] = Try {

    val coll : Collector[FlowEnvelope] = receiveMessages(headerCfg, cf, JmsDestination.create(destName).get, log)
    Await.result(coll.result, timeout + 100.millis)
  }

  protected def consumeEvents()(implicit timeout : FiniteDuration, system : ActorSystem, materializer : Materializer) : Try[List[FlowTransactionEvent]] = Try {
    consumeMessages(internal, "internal.transactions").get.map { env =>
      FlowTransactionEvent.envelope2event(headerCfg)(env).get
    }
  }

  protected def generateMessages(msgCount : Int)(f : FlowEnvelope => FlowEnvelope = { env => env }) : Try[Seq[FlowEnvelope]] = Try {

    1.to(msgCount).map { i =>
      FlowMessage(s"Message $i")(FlowMessage.noProps)
        .withHeader("UnitProperty", null).get
    }.map(FlowEnvelope.apply).map(f)
  }

  protected def sendMessages(destName : String, cf : IdAwareConnectionFactory)(msgs : FlowEnvelope*) : KillSwitch = {
    val pSettings : JmsProducerSettings = JmsProducerSettings(
      log = log,
      headerCfg = headerCfg,
      connectionFactory = cf,
      jmsDestination = Some(JmsDestination.create(destName).get)
    )

    sendMessages(pSettings, log, msgs : _*).get
  }
}

@RequiresForkedJVM
class InboundBridgeUntrackedSpec extends BridgeSpecSupport {

  override def baseDir : String = new File(BlendedTestSupport.projectTestOutput, "withoutTracking").getAbsolutePath()

  private def sendInbound(msgCount : Int) : KillSwitch = {
    val msgs : Seq[FlowEnvelope] = generateMessages(msgCount) { env =>
      env
        .withHeader(destinationName, s"sampleOut").get
    }.get

    sendMessages("sampleIn", external)(msgs : _*)
  }

  "The inbound bridge should" - {

    "process normal inbound messages without tracking" in {
      implicit val timeout : FiniteDuration = 1.second
      val msgCount = 2

      val switch = sendInbound(msgCount)

      val messages : List[FlowEnvelope] =
        consumeMessages(internal, "bridge.data.in.activemq.external")(1.second, system, materializer).get

      messages should have size msgCount

      messages.foreach { env =>
        env.header[Unit]("UnitProperty") should be(Some(()))
      }

      consumeEvents().get should be(empty)

      switch.shutdown()
    }

    "process text messages with a null body" in {

      implicit val timeout : FiniteDuration = 1.second

      val msg : FlowMessage = TextFlowMessage(null, FlowMessage.noProps)
      val msgs : Seq[FlowEnvelope] = Seq(FlowEnvelope(msg))

      val switch : KillSwitch = sendMessages("sampleIn", external)(msgs : _*)

      val messages : List[FlowEnvelope] =
        consumeMessages(internal, "bridge.data.in.activemq.external")(1.second, system, materializer).get

      messages should have size msgs.size

      consumeEvents().get should be(empty)

      switch.shutdown()
    }

    "process messages with an empty binary body" in {
      implicit val timeout : FiniteDuration = 1.second

      val msg : FlowMessage = BinaryFlowMessage(Array.empty[Byte], FlowMessage.noProps)
      val msgs : Seq[FlowEnvelope] = Seq(FlowEnvelope(msg))

      val switch : KillSwitch = sendMessages("sampleIn", external)(msgs : _*)

      val messages : List[FlowEnvelope] =
        consumeMessages(internal, "bridge.data.in.activemq.external")(1.second, system, materializer).get

      messages should have size msgs.size

      consumeEvents().get should be(empty)

      switch.shutdown()
    }
  }
}

@RequiresForkedJVM
class InboundBridgeTrackedSpec extends BridgeSpecSupport {

  private def sendInbound(msgCount : Int) : KillSwitch = {
    val msgs : Seq[FlowEnvelope] = generateMessages(msgCount) { env =>
      env
        .withHeader(destinationName, s"sampleOut").get
    }.get

    sendMessages("sampleIn", external)(msgs : _*)
  }

  "The Inbound Bridge should" - {

    // We only test for tracked transactions as all bridge inbound streams generate transaction started events by design
    "process normal inbound messages with tracked transactions" in {
      implicit val timeout : FiniteDuration = 1.second
      val msgCount = 2

      val switch = sendInbound(msgCount)

      val messages : List[FlowEnvelope] =
        consumeMessages(internal, "bridge.data.in.activemq.external")(1.second, system, materializer).get

      messages should have size (msgCount)

      messages.foreach { env =>
        env.header[Unit]("UnitProperty") should be(Some(()))
      }

      val envelopes : List[FlowTransactionEvent] = consumeEvents().get

      envelopes should have size (msgCount)
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
        headerCfg = headerCfg,
        connectionFactory = external,
        jmsDestination = Some(JmsQueue("SampleHeaderIn"))
      )

      val switch : KillSwitch = sendMessages(pSettings, log, env).get

      val result : List[FlowEnvelope] = consumeMessages(internal, "bridge.data.in.activemq.external").get

      result should have size 1
      result.head.header[String]("ResourceType") should be(Some(desc))

      switch.shutdown()
    }
  }
}

@RequiresForkedJVM
class InboundRejectBridgeSpec extends BridgeSpecSupport {

  private def sendInbound(msgCount : Int) : KillSwitch = {
    val msgs : Seq[FlowEnvelope] = generateMessages(msgCount) { env =>
      env
        .withHeader(destinationName, s"sampleOut").get
    }.get

    sendMessages("sampleIn", external)(msgs : _*)
  }

  override protected def bridgeActivator : BridgeActivator = new BridgeActivator() {
    override protected def streamBuilderFactory(system : ActorSystem)(materializer : Materializer)(cfg : BridgeStreamConfig) : BridgeStreamBuilder =
      new BridgeStreamBuilder(cfg)(system, materializer) {
        override protected def jmsSend : Flow[FlowEnvelope, FlowEnvelope, NotUsed] = Flow.fromFunction[FlowEnvelope, FlowEnvelope] { env =>
          env.withException(new Exception("Boom"))
        }
      }
  }

  "The inbound bridge should" - {

    "reject messages in case the send forward fails" in {
      implicit val timeout : FiniteDuration = 1.second
      val msgCount = 2

      val switch = sendInbound(msgCount)

      consumeMessages(internal, "bridge.data.in.activemq.external")(1.second, system, materializer).get should be(empty)
      consumeEvents().get should be(empty)

      consumeMessages(external, "sampleIn").get should have size (msgCount)

      switch.shutdown()
    }
  }

}

@RequiresForkedJVM
class OutboundBridgeSpec extends BridgeSpecSupport {

  private def sendOutbound(msgCount : Int, track : Boolean) : KillSwitch = {
    val msgs : Seq[FlowEnvelope] = generateMessages(msgCount) { env =>
      env
        .withHeader(destinationName, s"sampleOut").get
        .withHeader(headerCfg.headerTrack, track).get
    }.get

    sendMessages("bridge.data.out.activemq.external", internal)(msgs : _*)
  }

  "The outbound bridge should " - {

    "process normal inbound messages with untracked transactions" in {
      implicit val timeout : FiniteDuration = 1.second
      val msgCount = 2

      val switch = sendOutbound(msgCount, track = false)

      val messages : List[FlowEnvelope] =
        consumeMessages(external, "sampleOut").get

      messages should have size (msgCount)

      messages.foreach { env =>
        env.header[Unit]("UnitProperty") should be(Some(()))
      }

      val envelopes : List[FlowTransactionEvent] = consumeEvents().get

      envelopes should be(empty)

      switch.shutdown()
    }

    "process normal inbound messages with tracked transactions" in {
      implicit val timeout : FiniteDuration = 1.second
      val msgCount = 2

      val switch = sendOutbound(msgCount, true)

      val messages : List[FlowEnvelope] =
        consumeMessages(external, "sampleOut").get

      messages should have size (msgCount)

      messages.foreach { env =>
        env.header[Unit]("UnitProperty") should be(Some(()))
      }

      val envelopes : List[FlowTransactionEvent] = consumeEvents().get

      envelopes should have size (msgCount)
      assert(envelopes.forall(_.isInstanceOf[FlowTransactionUpdate]))

      switch.shutdown()
    }
  }
}

@RequiresForkedJVM
class SendFailedRetryBridgeSpec extends BridgeSpecSupport {

  private def sendOutbound(msgCount : Int, track : Boolean) : KillSwitch = {
    val msgs : Seq[FlowEnvelope] = generateMessages(msgCount) { env =>
      env
        .withHeader(destinationName, s"sampleOut").get
        .withHeader(headerCfg.headerTrack, track).get
    }.get

    sendMessages("bridge.data.out.activemq.external", internal)(msgs : _*)
  }

  // We override the send flow with a flow simply triggering an exception, so that the
  // exceptional path will be triggered
  override protected def bridgeActivator : BridgeActivator = new BridgeActivator() {
    override protected def streamBuilderFactory(system : ActorSystem)(materializer : Materializer)(cfg : BridgeStreamConfig) : BridgeStreamBuilder =
      new BridgeStreamBuilder(cfg)(system, materializer) {
        override protected def jmsSend : Flow[FlowEnvelope, FlowEnvelope, NotUsed] = Flow.fromFunction[FlowEnvelope, FlowEnvelope] { env =>
          env.withException(new Exception("Boom"))
        }
      }
  }

  "The outbound bridge should " - {

    "pass the message to the retry destination and not generate a transaction event if the forwarding of the message fails" in {
      implicit val timeout : FiniteDuration = 1.second
      val msgCount = 2

      val switch = sendOutbound(msgCount, true)

      val retried : List[FlowEnvelope] = consumeMessages(internal, "retries").get

      retried should have size (msgCount)

      retried.foreach { env =>
        env.header[Unit]("UnitProperty") should be(Some(()))
        env.header[String](headerCfg.headerRetryDestination) should be(Some("bridge.data.out.activemq.external"))
      }

      consumeEvents().get should be(empty)
      consumeMessages(external, "sampleOut").get should be(empty)

      switch.shutdown()
    }
  }
}

@RequiresForkedJVM
class SendFailedRejectBridgeSpec extends BridgeSpecSupport {

  override def baseDir : String = new File(BlendedTestSupport.projectTestOutput, "withoutRetries").getAbsolutePath()

  private def sendOutbound(msgCount : Int, track : Boolean) : KillSwitch = {
    val msgs : Seq[FlowEnvelope] = generateMessages(msgCount) { env =>
      env
        .withHeader(destinationName, s"sampleOut").get
        .withHeader(headerCfg.headerTrack, track).get
    }.get

    sendMessages("bridge.data.out.activemq.external", internal)(msgs : _*)
  }

  // We override the send flow with a flow simply triggering an exception, so that the
  // exceptional path will be triggered
  override protected def bridgeActivator : BridgeActivator = new BridgeActivator() {
    override protected def streamBuilderFactory(system : ActorSystem)(materializer : Materializer)(cfg : BridgeStreamConfig) : BridgeStreamBuilder =
      new BridgeStreamBuilder(cfg)(system, materializer) {
        override protected def jmsSend : Flow[FlowEnvelope, FlowEnvelope, NotUsed] = Flow.fromFunction[FlowEnvelope, FlowEnvelope] { env =>
          env.withException(new Exception("Boom"))
        }
      }
  }

  "The outbound bridge should " - {

    "reject the messages in a forward fails and no retry destination is defined" in {
      implicit val timeout : FiniteDuration = 1.second
      val msgCount = 2

      val switch = sendOutbound(msgCount, true)

      val retried : List[FlowEnvelope] = consumeMessages(internal, "retries").get
      retried should be(empty)

      consumeEvents().get should be(empty)

      retried.foreach { env =>
        env.header[Unit]("UnitProperty") should be(Some(()))
      }

      consumeMessages(internal, "bridge.data.out.activemq.external").get should have size (msgCount)

      switch.shutdown()
    }
  }
}

@RequiresForkedJVM
class TransactionSendFailedRetryBridgeSpec extends BridgeSpecSupport {

  private def sendOutbound(msgCount : Int, track : Boolean) : KillSwitch = {
    val msgs : Seq[FlowEnvelope] = generateMessages(msgCount) { env =>
      env
        .withHeader(destinationName, s"sampleOut").get
        .withHeader(headerCfg.headerTrack, track).get
    }.get

    sendMessages("bridge.data.out.activemq.external", internal)(msgs : _*)
  }

  override protected def bridgeActivator : BridgeActivator = new BridgeActivator() {
    override protected def streamBuilderFactory(system : ActorSystem)(materializer : Materializer)(cfg : BridgeStreamConfig) : BridgeStreamBuilder =
      new BridgeStreamBuilder(cfg)(system, materializer) {

        override protected def sendTransaction : Flow[FlowEnvelope, FlowEnvelope, NotUsed] =
          Flow.fromFunction[FlowEnvelope, FlowEnvelope] { env =>
            env.withException(new Exception("Boom !"))
          }
      }
  }

  "The outbound bridge should " - {

    "pass messages to the retry destination if the send of the transaction envelope fails" in {
      implicit val timeout : FiniteDuration = 1.second
      val msgCount = 2

      val switch = sendOutbound(msgCount, true)

      val retried : List[FlowEnvelope] = consumeMessages(internal, "retries").get
      retried should have size (msgCount)

      consumeEvents().get should be(empty)

      retried.foreach { env =>
        env.header[Unit]("UnitProperty") should be(Some(()))
      }

      consumeMessages(internal, "bridge.data.out.activemq.external").get should be(empty)

      switch.shutdown()
    }
  }
}

@RequiresForkedJVM
class TransactionSendFailedRejectBridgeSpec extends BridgeSpecSupport {

  override def baseDir : String = new File(BlendedTestSupport.projectTestOutput, "withoutRetries").getAbsolutePath()

  private def sendOutbound(msgCount : Int, track : Boolean) : KillSwitch = {
    val msgs : Seq[FlowEnvelope] = generateMessages(msgCount) { env =>
      env
        .withHeader(destinationName, s"sampleOut").get
        .withHeader(headerCfg.headerTrack, track).get
    }.get

    sendMessages("bridge.data.out.activemq.external", internal)(msgs : _*)
  }

  override protected def bridgeActivator : BridgeActivator = new BridgeActivator() {
    override protected def streamBuilderFactory(system : ActorSystem)(materializer : Materializer)(cfg : BridgeStreamConfig) : BridgeStreamBuilder =
      new BridgeStreamBuilder(cfg)(system, materializer) {

        override protected def sendTransaction : Flow[FlowEnvelope, FlowEnvelope, NotUsed] =
          Flow.fromFunction[FlowEnvelope, FlowEnvelope] { env =>
            env.withException(new Exception("Boom !"))
          }
      }
  }

  "The outbound bridge should " - {

    "reject envelopes if the send of the transaction event fails and retry is disabled" in {
      implicit val timeout : FiniteDuration = 1.second
      val msgCount = 2

      val switch = sendOutbound(msgCount, true)

      val retried : List[FlowEnvelope] = consumeMessages(internal, "retries").get
      retried should be(empty)

      consumeEvents().get should be(empty)

      consumeMessages(internal, "bridge.data.out.activemq.external").get should have size (2)

      switch.shutdown()
    }
  }
}
