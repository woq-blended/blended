package blended.jms.utils

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit}
import blended.jms.utils.internal._
import org.apache.activemq.ActiveMQConnectionFactory
import org.apache.activemq.broker.BrokerService
import org.apache.activemq.store.memory.MemoryPersistenceAdapter
import org.scalatest.{BeforeAndAfterAll, FreeSpecLike, Matchers}

import scala.concurrent.duration._

class JmsConnectionControllerSpec extends TestKit(ActorSystem("JmsController"))
  with FreeSpecLike
  with Matchers
  with ImplicitSender
  with BeforeAndAfterAll {

  private[this] var broker : Option[BrokerService] = None

  override protected def beforeAll() : Unit = {
    super.beforeAll()

    broker = {
      val b = new BrokerService()
      b.setBrokerName("blended")
      b.setPersistent(false)
      b.setUseJmx(false)
      b.setPersistenceAdapter(new MemoryPersistenceAdapter)

      b.start()
      b.waitUntilStarted()

      Some(b)
    }
  }

  override protected def afterAll(): Unit = {
    broker.foreach { b =>
      b.stop()
      b.waitUntilStopped()
    }
    broker = None
  }

  private[this] def connectionFactory(brokerName : String) = new ActiveMQConnectionFactory(s"vm://$brokerName?create=false")

  "The JMS Connection Controller" - {

    "should answer with a positive ConnectResult message in case of a succesful connect" in {

      val holder = new ConnectionHolder("happy", connectionFactory("blended"))
      val testActor = system.actorOf(JmsConnectionController.props(holder))

      val t = System.currentTimeMillis()

      testActor ! Connect(t)

      val m = receiveOne(3.seconds).asInstanceOf[ConnectResult]
      m.t should be (t)
      m.r.isRight should be (true)

      val t2 = System.currentTimeMillis()
      val c1 = m.r.right.get

      testActor ! Connect(t2)

      expectMsg(ConnectResult(t2, Right(c1)))
    }

    "should answer with a negative ConnectResult message in case of a failed connect" in {

      val holder = new ConnectionHolder("dirty", connectionFactory("foobar"))
      val testActor = system.actorOf(JmsConnectionController.props(holder))

      val t = System.currentTimeMillis()

      testActor ! Connect(t)

      val m = receiveOne(3.seconds).asInstanceOf[ConnectResult]
      m.t should be (t)
      m.r.isLeft should be (true)
    }

    "should answer with a ConnectionClosed message in case of a successful disconnect" in {
      val holder = new ConnectionHolder("happy", connectionFactory("blended"))
      val testActor = system.actorOf(JmsConnectionController.props(holder))

      val t = System.currentTimeMillis()

      testActor ! Connect(t)

      val m = receiveOne(3.seconds).asInstanceOf[ConnectResult]
      m.t should be (t)
      m.r.isRight should be (true)

      val t2 = System.currentTimeMillis()
      val c1 = m.r.right.get

      testActor ! Disconnect(1.seconds)
      expectMsg(ConnectionClosed)

    }

    "should answer with a CloseTimeout message in case a connection close timed out" in {
      val holder = new ConnectionHolder("happy", connectionFactory("blended")) {
        override def close(): Unit = {
          // spend a long time here
          Thread.sleep(5000)
        }
      }

      val testActor = system.actorOf(JmsConnectionController.props(holder))

      val t = System.currentTimeMillis()

      testActor ! Connect(t)

      val m = receiveOne(3.seconds).asInstanceOf[ConnectResult]
      m.t should be (t)
      m.r.isRight should be (true)

      val t2 = System.currentTimeMillis()
      val c1 = m.r.right.get

      testActor ! Disconnect(1.seconds)
      expectMsg(CloseTimeout)
    }
  }


}
