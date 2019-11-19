package blended.jms.bridge.internal

import java.io.File

import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, KillSwitch, Materializer}
import blended.activemq.brokerstarter.internal.BrokerActivator
import blended.akka.internal.BlendedAkkaActivator
import blended.container.context.api.ContainerIdentifierService
import blended.jms.utils.{IdAwareConnectionFactory, JmsDestination}
import blended.streams.internal.BlendedStreamsActivator
import blended.streams.jms.{JmsEnvelopeHeader, JmsProducerSettings, JmsStreamSupport}
import blended.streams.message.{FlowEnvelope, FlowEnvelopeLogger, FlowMessage}
import blended.streams.processor.Collector
import blended.streams.transaction.FlowTransactionEvent
import blended.streams.{BlendedStreamsConfig, FlowHeaderConfig}
import blended.testsupport.BlendedTestSupport
import blended.testsupport.pojosr.{BlendedPojoRegistry, PojoSrTestHelper, SimplePojoContainerSpec}
import blended.testsupport.scalatest.LoggingFreeSpecLike
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

  protected implicit val to : FiniteDuration = 5.seconds
  protected val log = Logger(getClass().getName())

  override def baseDir: String = new File(BlendedTestSupport.projectTestOutput, "withRetries").getAbsolutePath()

  protected def bridgeActivator : BridgeActivator = new BridgeActivator()

  override def bundles: Seq[(String, BundleActivator)] = Seq(
    "blended.akka" -> new BlendedAkkaActivator(),
    "blended.activemq.brokerstarter" -> new BrokerActivator(),
    "blended.streams" -> new BlendedStreamsActivator(),
    "blended.jms.bridge" -> bridgeActivator,
  )

  protected implicit val system : ActorSystem = mandatoryService[ActorSystem](registry)(None)
  protected implicit val materializer : ActorMaterializer = ActorMaterializer()
  protected implicit val ectxt : ExecutionContext = system.dispatcher

  protected val streamsCfg : BlendedStreamsConfig = mandatoryService[BlendedStreamsConfig](registry)(None)

  protected val (internal, external) = getConnectionFactories(registry)
  protected val idSvc : ContainerIdentifierService = mandatoryService[ContainerIdentifierService](registry)(None)

  protected val headerCfg : FlowHeaderConfig = FlowHeaderConfig.create(idSvc)
  protected val envLogger : FlowEnvelopeLogger = FlowEnvelopeLogger.create(headerCfg, log)

  protected def brokerFilter(provider : String) : String = s"(&(vendor=activemq)(provider=$provider))"

  protected def getConnectionFactories(sr: BlendedPojoRegistry) : (IdAwareConnectionFactory, IdAwareConnectionFactory) = {
    val cf1 = mandatoryService[IdAwareConnectionFactory](sr)(Some(brokerFilter("internal")))
    val cf2 = mandatoryService[IdAwareConnectionFactory](sr)(Some(brokerFilter("external")))
    (cf1, cf2)
  }

  protected def consumeMessages(
    cf: IdAwareConnectionFactory,
    destName : String,
    timeout : FiniteDuration
  )(implicit system : ActorSystem, materializer: Materializer) : Try[List[FlowEnvelope]] = Try {

    val coll : Collector[FlowEnvelope] = receiveMessages(
      headerCfg = headerCfg,
      cf = cf,
      dest = JmsDestination.create(destName).get,
      log = envLogger,
      timeout = Some(timeout)
    )
    Await.result(coll.result, timeout + 100.millis)
  }

  protected def consumeEvents(
    timeout : FiniteDuration
  )(implicit system : ActorSystem, materializer: Materializer) : Try[List[FlowTransactionEvent]] = Try {
    consumeMessages(cf = internal, destName = "internal.transactions", timeout = timeout).get.map{ env : FlowEnvelope =>
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
      log = envLogger,
      headerCfg = headerCfg,
      connectionFactory = cf,
      jmsDestination = Some(JmsDestination.create(destName).get)
    )

    sendMessages(pSettings, envLogger, msgs:_*).get
  }
}
