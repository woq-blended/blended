package blended.jms.utils.internal

import java.util.Date
import java.util.concurrent.atomic.AtomicInteger

import akka.actor.{Actor, ActorSystem, Props}
import akka.testkit.{ImplicitSender, TestKit}
import blended.jms.utils._
import blended.testsupport.scalatest.LoggingFreeSpecLike
import javax.jms.JMSException
import BlendedJMSConnectionConfig.defaultConfig

import scala.concurrent.duration._
import scala.util.{Success, Try}

class JmsConnectionControllerSpec extends TestKit(ActorSystem("JmsController"))
  with LoggingFreeSpecLike
  with ImplicitSender {

  private class EmptyActor extends Actor {
    override def receive: Receive = Actor.emptyBehavior
  }

  private[this] val idCnt = new AtomicInteger(0)

  def testId() : String = "testId-" + idCnt.incrementAndGet()

  def dummyResolver : String => Try[String] = { s => Success(s) }

  "The JMS Connection Controller" - {

    "should answer with a positive ConnectResult message in case of a successful connect" in {

      val holder = new DummyHolder(defaultConfig.copy("" +
        "ctrl", "positive"
      ))

      val testActor = system.actorOf(JmsConnectionController.props(holder, ConnectionCloseActor.props(holder)))

      val t = new Date()

      testActor ! Connect(t, testId())

      val m = receiveOne(3.seconds).asInstanceOf[ConnectResult]
      assert(m.ts == t)
      assert(m.r.isRight)

      val t2 = new Date()
      val c1 = m.r.right.get

      testActor ! Connect(t2, testId())

      expectMsg(ConnectResult(t2, Right(c1)))
    }

    "should answer with a negative ConnectResult message in case of a failed connect" in {

      val holder = new DummyHolder(cfg = defaultConfig.copy(
        vendor = "ctrl", provider = "negative"
      ), c => new DummyConnection(c) {
        throw new JMSException("boom")
      })

      val testActor = system.actorOf(JmsConnectionController.props(holder, ConnectionCloseActor.props(holder)))

      val t = new Date()

      testActor ! Connect(t, testId())

      val m = receiveOne(3.seconds).asInstanceOf[ConnectResult]
      assert(m.ts == t)
      assert(m.r.isLeft)
    }

    "should answer with a ConnectionClosed message in case of a successful disconnect" in {
      val holder = new DummyHolder(defaultConfig.copy(
        vendor = "ctrl", provider = "closed"
      ))

      val testActor = system.actorOf(JmsConnectionController.props(holder, ConnectionCloseActor.props(holder)))

      val t = new Date()

      testActor ! Connect(t, testId())

      val m = receiveOne(100.millis).asInstanceOf[ConnectResult]
      assert(m.ts == t)
      assert(m.r.isRight)

      testActor ! Disconnect(100.millis)
      expectMsg(ConnectionClosed)

    }

    "should answer with a CloseTimeout message in case a connection close timed out" in {

      val timeout = 100.millis

      val holder = new DummyHolder(defaultConfig.copy(
        vendor = "ctrl", provider = "closeTimeout"
      ), c => new DummyConnection(c) {
        override def close() : Unit = {
          Thread.sleep(timeout.toMillis * 2)
          super.close()
        }
      })

      val testActor = system.actorOf(JmsConnectionController.props(holder, ConnectionCloseActor.props(holder)))

      val t = new Date()
      testActor ! Connect(t, testId())

      val m = receiveOne(100.millis).asInstanceOf[ConnectResult]
      assert(m.ts == t)
      assert(m.r.isRight)

      testActor ! Disconnect(timeout)
      expectMsg(CloseTimeout)
    }

    "should answer with a CloseTimeout message in case the CloseActor does not respond" in {
      val timeout = 100.millis
      val holder = new DummyHolder(defaultConfig.copy(
        vendor = "ctrl", provider = "timeout2"
      ))

      val testActor = system.actorOf(JmsConnectionController.props(holder, Props(new EmptyActor())))

      val t = new Date()
      testActor ! Connect(t, testId())

      val m = receiveOne(100.millis).asInstanceOf[ConnectResult]
      assert(m.ts == t)
      assert(m.r.isRight)

      testActor ! Disconnect(timeout)
      expectMsg(CloseTimeout)
    }

    "should answer with a ConnectionClosed message in case the close runs into an exception" in {
      val timeout = 50.millis

      val holder = new DummyHolder(defaultConfig.copy(
        vendor = "ctrl", provider = "closeEx"
      ), c => new DummyConnection(c) {
        override def close() : Unit = {
          throw new JMSException("boom")
        }
      })

      val testActor = system.actorOf(JmsConnectionController.props(holder, ConnectionCloseActor.props(holder)))

      val t = new Date()
      testActor ! Connect(t, testId())

      val m = receiveOne(timeout).asInstanceOf[ConnectResult]
      assert(m.ts == t)
      assert(m.r.isRight)

      testActor ! Disconnect(4 * timeout)
      expectMsg(ConnectionClosed)
    }
  }
}
