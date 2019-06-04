package blended.jms.utils.internal

import java.util.Date
import java.util.concurrent.atomic.AtomicInteger

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit}
import javax.jms.JMSException
import org.scalatest.FreeSpecLike

import scala.concurrent.duration._
import scala.util.{Success, Try}

class JmsConnectionControllerSpec extends TestKit(ActorSystem("JmsController"))
  with FreeSpecLike
  with ImplicitSender {

  private[this] val idCnt = new AtomicInteger(0)

  def testId() : String = "testId-" + idCnt.incrementAndGet()

  def dummyResolver : String => Try[String] = { s => Success(s) }

  "The JMS Connection Controller" - {

    "should answer with a positive ConnectResult message in case of a succesful connect" in {

      val holder = new DummyHolder(() => new DummyConnection())
      val testActor = system.actorOf(JmsConnectionController.props(holder))

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

      val holder = new DummyHolder(() => {
        throw new JMSException("boom")
      })

      val testActor = system.actorOf(JmsConnectionController.props(holder))

      val t = new Date()

      testActor ! Connect(t, testId())

      val m = receiveOne(3.seconds).asInstanceOf[ConnectResult]
      assert(m.ts == t)
      assert(m.r.isLeft)
    }

    "should answer with a ConnectionClosed message in case of a successful disconnect" in {
      val holder = new DummyHolder(() => new DummyConnection())
      val testActor = system.actorOf(JmsConnectionController.props(holder))

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
      val holder = new DummyHolder(() => new DummyConnection() {
        override def close() : Unit = {
          Thread.sleep(timeout.toMillis * 2)
          super.close()
        }
      })

      val testActor = system.actorOf(JmsConnectionController.props(holder))

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

      val holder = new DummyHolder(() => new DummyConnection() {
        override def close() : Unit = {
          throw new JMSException("boom")
        }
      })

      val testActor = system.actorOf(JmsConnectionController.props(holder))

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
