package blended.jms.bridge.internal

import java.io.File

import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, KillSwitch, Materializer}
import blended.activemq.brokerstarter.internal.BrokerActivator
import blended.akka.internal.BlendedAkkaActivator
import blended.container.context.api.ContainerContext
import blended.jms.utils.{IdAwareConnectionFactory, JmsDestination}
import blended.streams.internal.BlendedStreamsActivator
import blended.streams.jms.{JmsEnvelopeHeader, JmsProducerSettings, JmsStreamSupport}
import blended.streams.message.{FlowEnvelope, FlowEnvelopeLogger, FlowMessage}
import blended.streams.processor.Collector
import blended.streams.transaction.FlowTransactionEvent
import blended.streams.{BlendedStreamsConfig, FlowHeaderConfig}
import blended.testsupport.BlendedTestSupport
import blended.testsupport.pojosr.{BlendedPojoRegistry, JmsConnectionHelper, PojoSrTestHelper, SimplePojoContainerSpec}
import blended.testsupport.scalatest.LoggingFreeSpecLike
import blended.util.logging.Logger
import org.osgi.framework.BundleActivator
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext}
import scala.util.Try

abstract class BridgeSpecSupport extends SimplePojoContainerSpec
  with LoggingFreeSpecLike
  with PojoSrTestHelper
  with Matchers
  with JmsStreamSupport
  with JmsEnvelopeHeader
  with ScalaCheckPropertyChecks
  with JmsConnectionHelper {

  override def timeout: FiniteDuration = 5.seconds
  private implicit val to : FiniteDuration = timeout

  protected val log : Logger = Logger(getClass().getName())

  override def baseDir: String = new File(BlendedTestSupport.projectTestOutput, "withRetries").getAbsolutePath()

  protected def bridgeActivator : BridgeActivator = new BridgeActivator()

  override def bundles: Seq[(String, BundleActivator)] = Seq(
    "blended.akka" -> new BlendedAkkaActivator(),
    "blended.activemq.brokerstarter" -> new BrokerActivator(),
    "blended.streams" -> new BlendedStreamsActivator(),
    "blended.jms.bridge" -> bridgeActivator,
  )

  protected val system : BlendedPojoRegistry => ActorSystem = r => mandatoryService[ActorSystem](r)
  protected val streamsCfg : BlendedPojoRegistry => BlendedStreamsConfig = r => mandatoryService[BlendedStreamsConfig](r)

  protected def brokerFilter(provider : String) : String = s"(&(vendor=activemq)(provider=$provider))"

  protected def getConnectionFactories(sr: BlendedPojoRegistry) : (IdAwareConnectionFactory, IdAwareConnectionFactory) = {
    val cf1 =  namedJmsConnectionFactory(sr, mustConnect = true, timeout = timeout)(vendor = "activemq", provider = "internal").get
    val cf2 =  namedJmsConnectionFactory(sr, mustConnect = true, timeout = timeout)(vendor = "activemq", provider = "external").get
    (cf1, cf2)
  }

  protected def consumeMessages(
    cf: IdAwareConnectionFactory,
    destName : String,
    timeout : FiniteDuration
  )(implicit system : ActorSystem) : Try[List[FlowEnvelope]] = Try {

    val coll : Collector[FlowEnvelope] = receiveMessages(
      headerCfg = headerCfg,
      cf = cf,
      dest = JmsDestination.create(destName).get,
      log = envLogger(log),
      timeout = Some(timeout)
    )
    Await.result(coll.result, timeout + 100.millis)
  }

  protected def consumeEvents(
    cf : IdAwareConnectionFactory,
    timeout : FiniteDuration
  )(implicit system : ActorSystem) : Try[List[FlowTransactionEvent]] = Try {
    consumeMessages(cf = cf, destName = "internal.transactions", timeout = timeout).get.map{ env : FlowEnvelope =>
      FlowTransactionEvent.envelope2event(headerCfg)(env).get
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
      log = envLogger(log),
      headerCfg = headerCfg,
      connectionFactory = cf,
      jmsDestination = Some(JmsDestination.create(destName).get)
    )

    sendMessages(pSettings, envLogger(log), msgs:_*)(system(registry)).get
  }
}
