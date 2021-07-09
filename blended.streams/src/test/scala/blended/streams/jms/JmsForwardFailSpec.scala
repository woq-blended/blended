package blended.streams.jms

import java.io.File

import blended.activemq.brokerstarter.internal.BrokerActivator
import blended.akka.internal.BlendedAkkaActivator
import blended.testsupport.BlendedTestSupport
import blended.testsupport.pojosr.{JmsConnectionHelper, PojoSrTestHelper, SimplePojoContainerSpec}
import blended.testsupport.scalatest.LoggingFreeSpecLike
import org.osgi.framework.BundleActivator
import org.scalatest.matchers.should.Matchers
import blended.jms.utils._
import blended.util.logging.Logger
import blended.streams.message.FlowEnvelope
import scala.concurrent.duration._
import akka.actor.ActorSystem
import scala.util.{Failure, Success}
import blended.streams.message.FlowEnvelopeLogger
import blended.streams.StreamFactories
import blended.util.logging.LogLevel
import scala.concurrent.Await
import blended.testsupport.RequiresForkedJVM

@RequiresForkedJVM
class JmsForwardFailSpec
    extends SimplePojoContainerSpec
    with LoggingFreeSpecLike
    with PojoSrTestHelper
    with JmsConnectionHelper
    with JmsStreamSupport
    with Matchers {

  override def baseDir: String = new File(BlendedTestSupport.projectTestOutput, "container").getAbsolutePath()

  override def bundles: Seq[(String, BundleActivator)] =
    Seq(
      "blended.akka" -> new BlendedAkkaActivator(),
      "blended.activemq.brokerstarter" -> new BrokerActivator()
    )

  protected def amqCf: IdAwareConnectionFactory = jmsConnectionFactory(registry, mustConnect = true).get

  protected def testDest: String => JmsDestination = d => JmsQueue(d)

  def producerSettings(destName: String, envLog: FlowEnvelopeLogger): JmsProducerSettings =
    JmsProducerSettings(
      log = envLog,
      headerCfg = headerCfg,
      connectionFactory = amqCf,
      jmsDestination = Some(testDest(destName))
    )

  def consumerSettings(destName: String, envLog: FlowEnvelopeLogger): JmsConsumerSettings =
    JmsConsumerSettings(log = envLog, headerCfg = headerCfg, connectionFactory = amqCf, ackTimeout = 1.second)
      .withAcknowledgeMode(AcknowledgeMode.ClientAcknowledge)
      .withSessionCount(1)
      .withDestination(Some(testDest(destName)))

  "Forwarding JMS messages to a non functional connection " - {

    "should not forward JMS messages if the destination connection is not functional" in {
      implicit val system: ActorSystem = actorSystem
      //implicit val eCtxt: ExecutionContext = system.dispatcher

      val destName = "forward"
      val log: Logger = Logger(getClass().getName())
      val envLog: FlowEnvelopeLogger = envLogger(log)
      val msg: FlowEnvelope = FlowEnvelope()

      val collected: FlowEnvelope => Boolean = { env =>
        envLog.logEnv(env, LogLevel.Debug, s"Acknowledging envelope [${env.id}]")
        env.deny()
        false
      }

      sendMessages(producerSettings(destName, envLog), envLogger(log), 1.second, msg) match {
        case Success(s) => succeed
        case Failure(t) => fail(t)
      }

      val collector = StreamFactories.runSourceWithTimeLimit(
        name = "forwardFailTest",
        source = jmsConsumer("consume", consumerSettings(destName, envLog), None),
        timeout = Some(60.seconds),
        onCollected = Some(collected),
        completeOn = None
      )

      val dlq = StreamFactories.runSourceWithTimeLimit(
        name = "forwardFailDlq",
        source = jmsConsumer("consumeDlq", consumerSettings("DLQ." + destName, envLog), None),
        timeout = Some(60.seconds),
        onCollected = Some { (env: FlowEnvelope) => env.acknowledge(); true },
        completeOn = Some((s: Seq[FlowEnvelope]) => s.nonEmpty)
      )

      Await.result(collector.result, 65.seconds)
      val deadMsg = Await.result(dlq.result, 65.seconds)

      assert(deadMsg.nonEmpty)

    }

    // "reuse a session if it is called with session id that that already exists" in {

    //   val sessionsOpened: AtomicInteger = new AtomicInteger(0)
    //   val mgr: JmsSessionManager = createSessionManger("reuse", 1) { _ => Try { sessionsOpened.incrementAndGet() } }
    //   checkForSession(sessionsOpened, mgr)
    //   checkForSession(sessionsOpened, mgr)
    // }

    // "not create more than maxSessions sessions" in {
    //   val sessionsOpened: AtomicInteger = new AtomicInteger(0)
    //   val mgr: JmsSessionManager = createSessionManger("noSpace", 1) { _ => Try { sessionsOpened.incrementAndGet() } }
    //   checkForSession(sessionsOpened, mgr)

    //   mgr.getSession("bar") match {
    //     case Failure(t)       => fail(t)
    //     case Success(Some(_)) => fail("Expected no second session created")
    //     case Success(None) =>
    //       assert(sessionsOpened.get() == 1)
    //   }
    // }

    // "yield a Failure(_) if the session creation throws an exception" in {

    //   val dummyConn: Connection = new Connection {
    //     override def createSession(transacted: Boolean, acknowledgeMode: Int): Session = throw new JMSException("Boom")
    //     override def getClientID: String = ???
    //     override def setClientID(clientID: String): Unit = ???
    //     override def getMetaData: ConnectionMetaData = ???
    //     override def getExceptionListener: ExceptionListener = ???
    //     override def setExceptionListener(listener: ExceptionListener): Unit = ???
    //     override def start(): Unit = ???
    //     override def stop(): Unit = ???
    //     override def close(): Unit = ???
    //     override def createConnectionConsumer(
    //       destination: Destination,
    //       messageSelector: String,
    //       sessionPool: ServerSessionPool,
    //       maxMessages: Int
    //     ): ConnectionConsumer = ???
    //     override def createDurableConnectionConsumer(
    //       topic: Topic,
    //       subscriptionName: String,
    //       messageSelector: String,
    //       sessionPool: ServerSessionPool,
    //       maxMessages: Int
    //     ): ConnectionConsumer = ???
    //   }

    //   val mgr: JmsSessionManager = new JmsSessionManager("fail", dummyConn, 1)

    //   intercept[JMSException] {
    //     mgr.getSession("foo").get
    //   }
    // }
  }
}
