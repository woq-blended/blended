package blended.jms.utils

import java.util.Date
import java.util.concurrent.atomic.AtomicInteger

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit}
import blended.jms.utils.internal._
import com.typesafe.config.{Config, ConfigFactory}
import org.apache.activemq.ActiveMQConnectionFactory
import org.apache.activemq.broker.BrokerService
import org.apache.activemq.store.memory.MemoryPersistenceAdapter
import org.scalatest.{BeforeAndAfterAll, FreeSpecLike, Matchers}

import scala.concurrent.duration._
import scala.collection.JavaConverters._
import scala.util.{Success, Try}

class JmsConnectionControllerSpec extends TestKit(ActorSystem("JmsController"))
  with FreeSpecLike
  with Matchers
  with ImplicitSender
  with BeforeAndAfterAll {

  private[this] val idCnt = new AtomicInteger(0)
  private[this] var broker : Option[BrokerService] = None

  def testId() : String = "testId-" + idCnt.incrementAndGet()

  def dummyResolver : String => Try[String] = { s => Success(s) }

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


  def cfConfig(
    vendor : String,
    provider : String,
    brokerName : String = "blended"
  ) : Config = {
    ConfigFactory.parseMap(Map(
      "vendor" -> vendor,
      "provider" -> provider,
      "properties.brokerURL" -> s"vm://$brokerName?create=false"
    ).asJava)
  }

  def jmsConfig(
    cfg : Config
  ) : BlendedJMSConnectionConfig = {
    BlendedJMSConnectionConfig.fromConfig(dummyResolver)(
      cfg.getString("vendor"),
      cfg.getString("provider"),
      cfg = cfg
    ).copy(
      clientId = "test",
      cfClassName = Some(classOf[ActiveMQConnectionFactory].getName())
    )
  }

  def connHolder(vendor: String, provider: String, brokerName: String = "blended") : ConnectionHolder = {

    val cfg = cfConfig(vendor, provider, brokerName)

    ConnectionHolder(
      config = jmsConfig(cfg),
      system = system
    )
  }

  "The JMS Connection Controller" - {

    "should answer with a positive ConnectResult message in case of a succesful connect" in {

      val holder = connHolder("happy", "happy")
      val testActor = system.actorOf(JmsConnectionController.props(holder))

      val t = new Date()

      testActor ! Connect(t, testId())

      val m = receiveOne(3.seconds).asInstanceOf[ConnectResult]
      m.ts should be (t)
      m.r.isRight should be (true)

      val t2 = new Date()
      val c1 = m.r.right.get

      testActor ! Connect(t2, testId())

      expectMsg(ConnectResult(t2, Right(c1)))
      holder.close()
    }

    "should answer with a negative ConnectResult message in case of a failed connect" in {

      val holder = connHolder("dirty", "dirty", "foo")
      val testActor = system.actorOf(JmsConnectionController.props(holder))

      val t = new Date()

      testActor ! Connect(t, testId())

      val m = receiveOne(3.seconds).asInstanceOf[ConnectResult]
      m.ts should be (t)
      m.r.isLeft should be (true)

      holder.close()
    }

    "should answer with a ConnectionClosed message in case of a successful disconnect" in {
      val holder = connHolder("happy", "happy")
      val testActor = system.actorOf(JmsConnectionController.props(holder))

      val t = new Date()

      testActor ! Connect(t, testId())

      val m = receiveOne(3.seconds).asInstanceOf[ConnectResult]
      m.ts should be (t)
      m.r.isRight should be (true)

      val t2 = System.currentTimeMillis()
      val c1 = m.r.right.get

      testActor ! Disconnect(1.seconds)
      expectMsg(ConnectionClosed)

    }

    "should answer with a CloseTimeout message in case a connection close timed out" in {

      val cfg = cfConfig("happy", "happy")

      val holder = new ConnectionHolder(
        config = BlendedJMSConnectionConfig.fromConfig(dummyResolver)(
          cfg.getString("vendor"),
          cfg.getString("provider"),
          cfg
        ).copy(cfClassName = Some(classOf[ActiveMQConnectionFactory].getName)),
        system = system
      ) {
        override def close(): Unit = {
          // spend a long time here
          Thread.sleep(5000)
        }
      }

      val testActor = system.actorOf(JmsConnectionController.props(holder))

      val t = new Date()

      testActor ! Connect(t, testId())

      val m = receiveOne(3.seconds).asInstanceOf[ConnectResult]
      m.ts should be (t)
      m.r.isRight should be (true)

      val t2 = System.currentTimeMillis()
      val c1 = m.r.right.get

      testActor ! Disconnect(1.seconds)
      expectMsg(CloseTimeout)
    }
  }
}
