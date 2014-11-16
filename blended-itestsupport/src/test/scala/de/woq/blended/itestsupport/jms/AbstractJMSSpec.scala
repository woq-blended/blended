package de.woq.blended.itestsupport.jms

import javax.jms.ConnectionFactory

import akka.actor.ActorRef
import akka.testkit.ImplicitSender
import de.woq.blended.testsupport.TestActorSys
import org.apache.activemq.ActiveMQConnectionFactory
import org.apache.activemq.broker.BrokerService
import org.apache.activemq.store.memory.MemoryPersistenceAdapter
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}

import de.woq.blended.itestsupport.jms.protocol._

abstract class AbstractJMSSpec extends TestActorSys
  with WordSpecLike
  with Matchers
  with BeforeAndAfterAll
  with ImplicitSender {

  protected var broker : Option[BrokerService] = None
  protected val cf : ConnectionFactory = new ActiveMQConnectionFactory("vm://blended")

  protected def connect(connector: ActorRef) : Unit = {
    connector ! Connect("test")
    expectMsg(Right(Connected("test")))
  }

  protected def disconnect(connector: ActorRef) : Unit = {
    connector ! Disconnect
    expectMsg(Right(Disconnected))
  }

  override protected def beforeAll() : Unit = {
    super.beforeAll()

    broker = {
      val b = new BrokerService()
      b.setBrokerName("blended")
      b.setPersistent(false)
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
