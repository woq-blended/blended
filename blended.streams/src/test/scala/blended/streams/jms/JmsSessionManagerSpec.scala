package blended.streams.jms

import java.io.File
import java.util.concurrent.atomic.AtomicInteger

import akka.actor.ActorSystem
import akka.testkit.TestProbe
import blended.activemq.brokerstarter.internal.BrokerActivator
import blended.akka.internal.BlendedAkkaActivator
import blended.jms.utils.{Connected, ConnectionStateChanged, IdAwareConnectionFactory, JmsSession}
import blended.testsupport.BlendedTestSupport
import blended.testsupport.pojosr.{PojoSrTestHelper, SimplePojoContainerSpec}
import blended.testsupport.scalatest.LoggingFreeSpecLike
import javax.jms._
import org.osgi.framework.BundleActivator
import org.scalatest.Matchers

import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}

class JmsSessionManagerSpec extends SimplePojoContainerSpec
  with LoggingFreeSpecLike
  with PojoSrTestHelper
  with Matchers {

  override def baseDir : String = new File(BlendedTestSupport.projectTestOutput, "container").getAbsolutePath()

  override def bundles : Seq[(String, BundleActivator)] = Seq(
    "blended.akka" -> new BlendedAkkaActivator(),
    "blended.activemq.brokerstarter" -> new BrokerActivator()
  )

  private implicit val timeout : FiniteDuration = 3.seconds
  private implicit val system : ActorSystem = mandatoryService[ActorSystem](registry)(None)
  private val cf : IdAwareConnectionFactory = mandatoryService[IdAwareConnectionFactory](registry)(None)
  private val con : Connection = {
    val probe : TestProbe = TestProbe()
    system.eventStream.subscribe(probe.ref, classOf[ConnectionStateChanged])
    probe.fishForMessage(3.seconds){
      case ConnectionStateChanged(state) =>
        state.vendor == "activemq" && state.provider == "activemq" && state.status == Connected
    }

    cf.createConnection()
  }

  private def createSessionManger(name : String, maxSessions : Int)(sessionOpened : JmsSession => Try[Unit]) : JmsSessionManager = new JmsSessionManager(
    name = name,
    conn = con,
    maxSessions = maxSessions
  ) {
    override def onSessionOpen: JmsSession => Try[Unit] = sessionOpened
  }

  private def checkForSession(sessionCount : AtomicInteger, mgr : JmsSessionManager) : Unit = {

    mgr.getSession("foo") match {
      case Success(Some(_)) =>
        assert(sessionCount.get() == 1)
      case Success(None) =>
        fail("Expected open session")
      case Failure(t) =>
        fail(t)
    }
  }

  "A JMS session manager should" - {

    "create a new session if it is called with session id that does not yet exist" in {
      val sessionsOpened : AtomicInteger = new AtomicInteger(0)
      checkForSession(sessionsOpened, createSessionManger("single", 1){ _ => Try { sessionsOpened.incrementAndGet() }})
    }

    "reuse a session if it is called with session id that that already exists" in {

      val sessionsOpened : AtomicInteger = new AtomicInteger(0)
      val mgr : JmsSessionManager = createSessionManger("reuse", 1){ _ => Try { sessionsOpened.incrementAndGet() }}
      checkForSession(sessionsOpened, mgr)
      checkForSession(sessionsOpened, mgr)
    }

    "not create more than maxSessions sessions" in {
      val sessionsOpened : AtomicInteger = new AtomicInteger(0)
      val mgr : JmsSessionManager = createSessionManger("noSpace", 1){ _ => Try { sessionsOpened.incrementAndGet() }}
      checkForSession(sessionsOpened, mgr)

      mgr.getSession("bar") match {
        case Failure(t) => fail(t)
        case Success(Some(_)) => fail("Expected no second session created")
        case Success(None) =>
          assert(sessionsOpened.get() == 1)
      }
    }

    "yield a Failure(_) if the session creation throws an exception" in {

      val dummyConn : Connection = new Connection {
        override def createSession(transacted: Boolean, acknowledgeMode: Int): Session = throw new JMSException("Boom")
        override def getClientID: String = ???
        override def setClientID(clientID: String): Unit = ???
        override def getMetaData: ConnectionMetaData = ???
        override def getExceptionListener: ExceptionListener = ???
        override def setExceptionListener(listener: ExceptionListener): Unit = ???
        override def start(): Unit = ???
        override def stop(): Unit = ???
        override def close(): Unit = ???
        override def createConnectionConsumer(destination: Destination, messageSelector: String, sessionPool: ServerSessionPool, maxMessages: Int): ConnectionConsumer = ???
        override def createDurableConnectionConsumer(topic: Topic, subscriptionName: String, messageSelector: String, sessionPool: ServerSessionPool, maxMessages: Int): ConnectionConsumer = ???
      }

      val mgr : JmsSessionManager = new JmsSessionManager("fail", dummyConn, 1)

      intercept[JMSException] {
        mgr.getSession("foo").get
      }
    }
  }
}
