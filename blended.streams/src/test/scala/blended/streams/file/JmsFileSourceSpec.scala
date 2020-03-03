package blended.streams.file

import java.io.File
import java.util.concurrent.atomic.AtomicInteger

import akka.NotUsed
import akka.actor.{ActorRef, ActorSystem}
import akka.stream.scaladsl.Source
import akka.stream.{ActorMaterializer, Materializer}
import akka.testkit.TestProbe
import blended.akka.internal.BlendedAkkaActivator
import blended.container.context.api.ContainerContext
import blended.jms.utils.{IdAwareConnectionFactory, JmsDestination, SimpleIdAwareConnectionFactory}
import blended.streams.jms._
import blended.streams.message.{FlowEnvelope, FlowEnvelopeLogger}
import blended.streams.processor.AckProcessor
import blended.streams.{BlendedStreamsConfig, FlowHeaderConfig, FlowProcessor, StreamController}
import blended.testsupport.BlendedTestSupport
import blended.testsupport.pojosr.{PojoSrTestHelper, SimplePojoContainerSpec}
import blended.testsupport.scalatest.LoggingFreeSpecLike
import blended.util.RichTry._
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

  override def baseDir : String = s"${BlendedTestSupport.projectTestOutput}/container"

  override def bundles : Seq[(String, BundleActivator)] = Seq(
    "blended.akka" -> new BlendedAkkaActivator()
  )

  private implicit val timeout : FiniteDuration = 1.second
  private val ctCtxt : ContainerContext = mandatoryService[ContainerContext](registry)(None)
  private val headerCfg : FlowHeaderConfig = FlowHeaderConfig.create(ctCtxt)
  private val streamCfg : BlendedStreamsConfig = BlendedStreamsConfig.create(ctCtxt)

  private implicit val system : ActorSystem = mandatoryService[ActorSystem](registry)(None)
  private implicit val materializer : Materializer = ActorMaterializer()

  private val log : Logger = Logger[JmsFileSourceSpec]
  private val envLogger : FlowEnvelopeLogger = FlowEnvelopeLogger.create(headerCfg, log)

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

    val dest = JmsDestination.create(destName).unwrap

    val settings : JmsConsumerSettings = JmsConsumerSettings(
      log = envLogger,
      headerCfg = headerCfg,
      connectionFactory = cf,
      jmsDestination = Some(dest),
      acknowledgeMode = AcknowledgeMode.ClientAcknowledge
    )

    Source.fromGraph(new JmsConsumerStage(name, settings, minMessageDelay = None))
      .via(FlowProcessor.fromFunction("publish", envLogger){ env => Try {
        system.eventStream.publish(EnvelopeReceived(env))
        env
      }})
      .via(new AckProcessor(name + ".ack").flow)
  }

  private def producerSettings(
    cf : IdAwareConnectionFactory,
    destName : String
  ) : JmsProducerSettings = {

    val dest : JmsDestination = JmsDestination.create(destName).unwrap
    JmsProducerSettings(
      log = envLogger,
      connectionFactory = cf,
      headerCfg = headerCfg,
      jmsDestination = Some(dest)
    )
  }

  "A file source connected to a JmsSinkStage should" - {

    val rawCfg : Config = ctCtxt.containerConfig.getConfig("simplePoll")

    def setupStreams(
      name : String,
      destName : String,
      cf : IdAwareConnectionFactory,
      srcDir : String
    ) : Seq[ActorRef] = {

      val pollCfg : FilePollConfig = FilePollConfig(rawCfg, ctCtxt).copy(
        sourceDir = BlendedTestSupport.projectTestOutput + s"/$srcDir"
      )

      prepareDirectory(pollCfg.sourceDir)

      val src : Source[FlowEnvelope, NotUsed] = Source.fromGraph(new FileAckSource(pollCfg, envLogger))
        .via(jmsProducer(s"$name.send", producerSettings(cf, destName), autoAck = true))

      val fileController : ActorRef =
        system.actorOf(StreamController.props[FlowEnvelope, NotUsed](
          streamName = name + ".controller",
          src = src,
          streamCfg = streamCfg
        )(
          onMaterialize = _ => ()
        ))

      val msgController : ActorRef = system.actorOf(StreamController.props[FlowEnvelope, NotUsed](
        streamName = name,
        src = msgConsumer(destName, cf, destName),
        streamCfg = streamCfg
      )(onMaterialize = _ => ()))

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

    "pickup files and send them to JMS" in {

      val (b, p) = startBroker("normal")
      val cf = amqCf(p)
      val srcDir = "jmsPoll"

      val controller : Seq[ActorRef] = setupStreams("simple", "simpleJmsPoll", cf, srcDir)
      testWithJms(10.seconds, srcDir)

      controller.foreach(_ ! StreamController.Stop)
      // scalastyle:off magic.number
      Thread.sleep(1000)
      // scalastyle:on magic.number

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
