package blended.itestsupport.jms

import javax.jms.ConnectionFactory

import akka.actor.{ActorRef, ActorSystem}
import akka.testkit.TestProbe
import blended.itestsupport.jms.protocol._
import org.apache.activemq.ActiveMQConnectionFactory
import org.apache.activemq.broker.BrokerService
import org.apache.activemq.store.memory.MemoryPersistenceAdapter
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpec}

abstract class AbstractJMSSpec extends WordSpec
  with Matchers
  with BeforeAndAfterAll {

  protected var broker : Option[BrokerService] = None
  protected val cf : ConnectionFactory = new ActiveMQConnectionFactory("vm://blended")

  protected def connect(connector: ActorRef)(implicit system: ActorSystem) : Unit = {
    val probe = TestProbe()
    connector.tell(Connect("test"), probe.ref)
    probe.expectMsg(Right(Connected("test")))
  }

  protected def disconnect(connector: ActorRef)(implicit system: ActorSystem) : Unit = {
    val probe = TestProbe()
    connector.tell(Disconnect, probe.ref)
    probe.expectMsg(Right(Disconnected))
  }

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

  override protected def afterAll() : Unit = {
    super.afterAll()

    broker.foreach { b =>
      b.stop()
      b.waitUntilStopped()
    }
    broker = None
  }
}
