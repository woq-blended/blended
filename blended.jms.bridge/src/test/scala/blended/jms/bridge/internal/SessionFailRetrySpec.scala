package blended.jms.bridge.internal

import akka.stream.KillSwitch
import blended.jms.utils.IdAwareConnectionFactory
import blended.streams.message.FlowEnvelope
import blended.testsupport.RequiresForkedJVM

import scala.concurrent.duration._
import org.osgi.framework.BundleActivator
import blended.jms.utils.BlendedSingleConnectionFactory
import blended.jms.utils.BlendedJMSConnectionConfig
import domino.DominoActivator
import blended.akka.ActorSystemWatching
import blended.jms.utils.BlendedJMSConnection
import javax.jms._
import blended.testsupport.BlendedTestSupport
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

@RequiresForkedJVM
class SessionFailRetrySpec extends BridgeSpecSupport {

  override def baseDir: String = new File(BlendedTestSupport.projectTestOutput, "brokenExternal").getAbsolutePath()

  private val sessionOk: AtomicBoolean = new AtomicBoolean(true)

  private val extActivator = new DominoActivator with ActorSystemWatching {
    whenBundleActive {
      whenActorSystemAvailable { cfg =>
        val external = new BlendedSingleConnectionFactory(
          config = BlendedJMSConnectionConfig.defaultConfig.copy(
            vendor = "activemq",
            provider = "external",
            keepAliveEnabled = false,
            jmxEnabled = false,
            cf = Some(new ConnectionFactory {
              override def createConnection(): Connection = {
                val con = new Connection {

                  override def createSession(x$1: Boolean, x$2: Int): Session =
                    if (sessionOk.get()) {
                      new Session {

                        override def createBytesMessage(): BytesMessage = throw new JMSException("Boom")

                        override def createMapMessage(): MapMessage = throw new JMSException("Boom")

                        override def createMessage(): Message = throw new JMSException("Boom")

                        override def createObjectMessage(): ObjectMessage = throw new JMSException("Boom")

                        override def createObjectMessage(x$1: Serializable): ObjectMessage =
                          throw new JMSException("Boom")

                        override def createStreamMessage(): StreamMessage = throw new JMSException("Boom")

                        override def createTextMessage(): TextMessage = throw new JMSException("Boom")

                        override def createTextMessage(x$1: String): TextMessage = throw new JMSException("Boom")

                        override def getTransacted(): Boolean = throw new JMSException("Boom")

                        override def getAcknowledgeMode(): Int = throw new JMSException("Boom")

                        override def commit(): Unit = throw new JMSException("Boom")

                        override def rollback(): Unit = throw new JMSException("Boom")

                        override def close(): Unit = throw new JMSException("Boom")

                        override def recover(): Unit = throw new JMSException("Boom")

                        override def getMessageListener(): MessageListener = throw new JMSException("Boom")

                        override def setMessageListener(x$1: MessageListener): Unit = throw new JMSException("Boom")

                        override def run(): Unit = throw new JMSException("Boom")

                        override def createProducer(x$1: Destination): MessageProducer = throw new JMSException("Boom")

                        override def createConsumer(x$1: Destination): MessageConsumer = throw new JMSException("Boom")

                        override def createConsumer(x$1: Destination, x$2: String): MessageConsumer =
                          throw new JMSException("Boom")

                        override def createConsumer(x$1: Destination, x$2: String, x$3: Boolean): MessageConsumer =
                          throw new JMSException("Boom")

                        override def createQueue(x$1: String): Queue = throw new JMSException("Boom")

                        override def createTopic(x$1: String): Topic = throw new JMSException("Boom")

                        override def createDurableSubscriber(x$1: Topic, x$2: String): TopicSubscriber =
                          throw new JMSException("Boom")

                        override def createDurableSubscriber(x$1: Topic, x$2: String, x$3: String, x$4: Boolean)
                          : TopicSubscriber = throw new JMSException("Boom")

                        override def createBrowser(x$1: Queue): QueueBrowser = throw new JMSException("Boom")

                        override def createBrowser(x$1: Queue, x$2: String): QueueBrowser =
                          throw new JMSException("Boom")

                        override def createTemporaryQueue(): TemporaryQueue = throw new JMSException("Boom")

                        override def createTemporaryTopic(): TemporaryTopic = throw new JMSException("Boom")

                        override def unsubscribe(x$1: String): Unit = throw new JMSException("Boom")

                      }
                    } else {
                      throw new JMSException("Cant create session")
                    }

                  override def getClientID(): String = "foo"

                  override def setClientID(x$1: String): Unit = {}

                  override def getMetaData(): ConnectionMetaData = ???

                  override def getExceptionListener(): ExceptionListener = ???

                  override def setExceptionListener(x$1: ExceptionListener): Unit = {}

                  override def start(): Unit = {}

                  override def stop(): Unit = {}

                  override def close(): Unit = {}

                  override def createConnectionConsumer(x$1: Destination, x$2: String, x$3: ServerSessionPool, x$4: Int)
                    : ConnectionConsumer = ???

                  override def createDurableConnectionConsumer(
                    x$1: Topic,
                    x$2: String,
                    x$3: String,
                    x$4: ServerSessionPool,
                    x$5: Int
                  ): ConnectionConsumer = ???

                }
                new BlendedJMSConnection("activemq", "external", con)
              }
              override def createConnection(user: String, password: String): Connection = createConnection()
            })
          ),
          Some(bundleContext)
        )(cfg.system)
        external.providesService[ConnectionFactory, IdAwareConnectionFactory](
          Map("vendor" -> "activemq", "provider" -> "external", "brokerName" -> "broker2")
        )
      }
    }

  }

  override def bundles: Seq[(String, BundleActivator)] = super.bundles ++ Seq(("bridge.external", extActivator))

  private def sendOutbound(
    cf: IdAwareConnectionFactory,
    timeout: FiniteDuration,
    msgCount: Int,
    track: Boolean
  ): KillSwitch = {
    val msgs: Seq[FlowEnvelope] = generateMessages(msgCount) { env =>
      env
        .withHeader(destHeader(headerCfg.prefix), s"sampleOut")
        .get
        .withHeader(headerCfg.headerTrack, track)
        .get
    }.get

    sendMessages("bridge.data.out.activemq.external", cf, timeout)(msgs: _*)
  }

  "The outbound bridge should " - {

    "forward messages to the retry queue in case a session for the outbound jms provider could not be created" in logException {
      val timeout: FiniteDuration = 30.seconds
      val msgCount = 2

      val actorSys = system(registry)
      val internal = namedJmsConnectionFactory(registry, mustConnect = true, timeout = timeout)(
        vendor = "activemq",
        provider = "internal"
      ).get

      val switch = sendOutbound(internal, timeout, msgCount, track = false)

      3.until(registry.getBundleContext().getBundles().size).foreach { i =>
        registry.getBundleContext().getBundle(i).stop()
      }
      Thread.sleep(5000)

      val messages: List[FlowEnvelope] =
        consumeMessages(
          cf = internal,
          destName = "bridge.data.out.activemq.external",
          expected = msgCount,
          timeout = timeout
        )(actorSys).get

      messages should have size (msgCount)

      messages.foreach { env =>
        env.header[Unit]("UnitProperty") should be(Some(()))
      }

      switch.shutdown()
    }

  }
}
