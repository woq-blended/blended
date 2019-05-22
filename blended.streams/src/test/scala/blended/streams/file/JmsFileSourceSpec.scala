package blended.streams.file

import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

import akka.NotUsed
import akka.actor.{ActorRef, ActorSystem}
import akka.stream.scaladsl.Source
import akka.stream.{ActorMaterializer, Materializer}
import akka.testkit.TestProbe
import blended.akka.internal.BlendedAkkaActivator
import blended.container.context.api.ContainerIdentifierService
import blended.jms.utils.{IdAwareConnectionFactory, JmsDestination, SimpleIdAwareConnectionFactory}
import blended.streams.jms._
import blended.streams.message.FlowEnvelope
import blended.streams.processor.AckProcessor
import blended.streams.transaction.FlowHeaderConfig
import blended.streams.{FlowProcessor, StreamController, StreamControllerConfig}
import blended.testsupport.BlendedTestSupport
import blended.testsupport.pojosr.{PojoSrTestHelper, SimplePojoContainerSpec}
import blended.testsupport.scalatest.LoggingFreeSpecLike
import blended.util.logging.Logger
import com.typesafe.config.Config
import org.apache.activemq.ActiveMQConnectionFactory
import org.apache.activemq.broker.BrokerService
import org.apache.activemq.store.memory.MemoryPersistenceAdapter
import org.osgi.framework.BundleActivator
import org.scalatest.Matchers

import scala.concurrent.duration._
import scala.util.Try

class JmsFileSourceSpec extends SimplePojoContainerSpec
  with PojoSrTestHelper
  with LoggingFreeSpecLike
  with Matchers
  with JmsStreamSupport
  with FileSourceTestSupport {

  override def baseDir: String = s"${BlendedTestSupport.projectTestOutput}/container"

  override def bundles: Seq[(String, BundleActivator)] = Seq(
    "blended.akka" -> new BlendedAkkaActivator()
  )

  private implicit val timeout : FiniteDuration = 1.second
  private val idSvc : ContainerIdentifierService = mandatoryService[ContainerIdentifierService](registry)(None)
  private val headerCfg : FlowHeaderConfig = FlowHeaderConfig.create(idSvc)

  private implicit val system : ActorSystem = mandatoryService[ActorSystem](registry)(None)
  private implicit val materializer : Materializer = ActorMaterializer()

  private val log : Logger = Logger[JmsFileSourceSpec]

  private case class EnvelopeReceived(env : FlowEnvelope)

  private val cfCnt : AtomicInteger = new AtomicInteger(0)
  private def amqCf(p : Int) : IdAwareConnectionFactory = SimpleIdAwareConnectionFactory(
    vendor = "amq",
    provider = s"${cfCnt.incrementAndGet()}",
    clientId = "spec",
    cf = new ActiveMQConnectionFactory(s"tcp://localhost:$p?jms.prefetchPolicy.queuePrefetch=10"),
    minReconnect = 5.seconds
  )

  private def startBroker(brokerName : String, port : Int = 0) : (BrokerService, Int) = {

    val b = new BrokerService()
    b.setBrokerName(brokerName)
    b.setPersistent(false)
    b.setUseJmx(false)
    b.setPersistenceAdapter(new MemoryPersistenceAdapter)

    b.setDedicatedTaskRunner(true)

    b.addConnector(s"tcp://localhost:${port}")

    b.start()
    b.waitUntilStarted()

    val p : Int = b.getTransportConnectors().get(0).getUri().getPort()
    log.info(s"Jms Broker started at port [$p]")

    (b, p)
  }

  private def stopBroker(bs : BrokerService) : Unit = {
    bs.stop()
    bs.waitUntilStopped()
  }

  private def msgConsumer(
    name : String,
    cf : IdAwareConnectionFactory,
    destName : String
  ) : Source[FlowEnvelope, NotUsed] = {

    val dest = JmsDestination.create(destName).get

    val settings : JMSConsumerSettings = JMSConsumerSettings(
      log = log,
      headerCfg = headerCfg,
      connectionFactory = cf,
      jmsDestination = Some(dest),
      acknowledgeMode = AcknowledgeMode.ClientAcknowledge,
      sessionRecreateTimeout = 100.millis
    )

    Source.fromGraph(new JmsAckSourceStage(name, settings, minMessageDelay = None))
      .via(FlowProcessor.fromFunction("publish", log){ env => Try {
        system.eventStream.publish(EnvelopeReceived(env))
        env
      }})
      .via(new AckProcessor(name + ".ack").flow)
  }

  private def producerSettings(
    cf : IdAwareConnectionFactory,
    destName : String
  ) : JmsProducerSettings = {

    val dest : JmsDestination = JmsDestination.create(destName).get
    JmsProducerSettings(
      log = log,
      connectionFactory = cf,
      headerCfg = headerCfg,
      jmsDestination = Some(dest),
      sessionRecreateTimeout = 100.millis
    )
  }

  "A file source connected to a JmsSinkStage should" - {

    val rawCfg : Config = idSvc.getContainerContext().getContainerConfig().getConfig("simplePoll")

    def setupStreams(
      name : String,
      destName : String,
      cf : IdAwareConnectionFactory,
      srcDir : String
    ) : Seq[ActorRef] = {

      val pollCfg : FilePollConfig = FilePollConfig(rawCfg, idSvc).copy(
        sourceDir = BlendedTestSupport.projectTestOutput + s"/$srcDir"
      )

      prepareDirectory(pollCfg.sourceDir)

      val src : Source[FlowEnvelope, NotUsed] = Source.fromGraph(new FileAckSource(pollCfg))
        .via(jmsProducer(s"$name.send", producerSettings(cf, destName), autoAck = true))

      val ctrlConfig : StreamControllerConfig = StreamControllerConfig(
        name = name,
        minDelay = 2.seconds,
        maxDelay = 10.seconds,
        exponential = false,
        onFailureOnly = false,
        random = 0.2
      )

      val fileController : ActorRef =
        system.actorOf(StreamController.props[FlowEnvelope, NotUsed](src, ctrlConfig.copy(name = name + ".controller")))
      val msgController : ActorRef = system.actorOf(StreamController.props[FlowEnvelope, NotUsed](
        msgConsumer(destName, cf, destName), ctrlConfig.copy(name = name + ".consumer")
      ))

      Seq(fileController, msgController)
    }

    def testWithJms(
      t : FiniteDuration,
      srcDir : String
    ) : Unit = {

      val probe : TestProbe = TestProbe()
      system.eventStream.subscribe(probe.ref, classOf[EnvelopeReceived])

      genFile(new File(BlendedTestSupport.projectTestOutput + "/" + srcDir, "test.txt"))

      probe.expectMsgType[EnvelopeReceived](t)
      system.stop(probe.ref)
    }

    "pickup file and send them to JMS" in {

      val (b, p) = startBroker("normal")
      val cf = amqCf(p)
      val srcDir = "jmsPoll"

      val controller : Seq[ActorRef] = setupStreams("simple", "simpleJmsPoll", cf, srcDir)
      testWithJms(10.seconds, srcDir)

      controller.foreach(_ ! StreamController.Stop)

      stopBroker(b)
    }

    "recover to send JMS messages in case the JMS connection fails and comes back" in {

      val (b, p) = startBroker("failover1")
      val cf = amqCf(p)
      val srcDir = "jmsFailOver"

      val controller : Seq[ActorRef] = setupStreams("failOver", "failOverPoll", cf, srcDir)
      testWithJms(10.seconds, srcDir)

      val probe : TestProbe = TestProbe()
      system.eventStream.subscribe(probe.ref, classOf[EnvelopeReceived])

      probe.expectNoMessage(3.seconds)

      stopBroker(b)

      probe.expectNoMessage(3.seconds)

      startBroker("failover2", p)
      testWithJms(30.seconds, srcDir)

      controller.foreach(_ ! StreamController.Stop)
    }
  }
}
