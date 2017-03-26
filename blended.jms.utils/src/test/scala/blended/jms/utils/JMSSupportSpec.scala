package blended.jms.utils

import java.util.concurrent.atomic.AtomicInteger
import javax.jms.{DeliveryMode, Message, Session}

import akka.actor.{ActorSystem, Props}
import akka.camel.CamelExtension
import akka.testkit.{TestKit, TestProbe}
import akka.util.Timeout
import blended.testsupport.camel.{CamelMockActor, CamelTestSupport}
import org.apache.activemq.ActiveMQConnectionFactory
import org.apache.activemq.broker.BrokerService
import org.apache.activemq.store.memory.MemoryPersistenceAdapter
import org.apache.camel.CamelContext
import org.apache.camel.component.jms.JmsComponent
import org.scalatest.{BeforeAndAfterAll, FreeSpec, Matchers}

import scala.concurrent.duration._
import blended.testsupport.camel.MockAssertions._
import blended.testsupport.camel.protocol._

class JMSSupportSpec extends FreeSpec
  with JMSSupport
  with CamelTestSupport
  with Matchers
  with BeforeAndAfterAll {

  private[this] var broker : Option[BrokerService] = None
  private[this] val cf = new ActiveMQConnectionFactory("vm://blended?create=false")

  implicit val testkit = new TestKit(ActorSystem("JMSSupportSpec"))
  implicit val system = testkit.system

  implicit val timeout : Timeout = 3.seconds

  override val camelContext: CamelContext = {
    val ctxt = CamelExtension(system).context
    ctxt.addComponent("jms", JmsComponent.jmsComponent(cf))
    ctxt
  }

  private def sendMessage() : Unit = {
    sendMessage(
      cf = cf,
      destName = "test",
      content = None,
      msgFactory = new JMSMessageFactory {
        override def createMessage(session: Session, content: Option[Any]) = {
          session.createTextMessage("Hello AMQ")
        }
      },
      deliveryMode = DeliveryMode.NON_PERSISTENT
    )
  }

  private def checkMessage(assertions: MockAssertion*) : Unit = {

    val probe = TestProbe()
    val mock = system.actorOf(Props(new CamelMockActor("jms:test")))
    system.eventStream.subscribe(probe.ref, classOf[MockMessageReceived])

    probe.receiveOne(timeout.duration)
    mock ! StopReceive

    val errors = checkAssertions(mock, assertions:_*)
    errors should be (empty)

  }

  "The JmsSupport should" - {

    "send messages correctly to JMS" in {

      sendMessage()
      checkMessage(expectedMessageCount(1))
    }

    "should receive messages from JMS correctly" in {

      val count : AtomicInteger = new AtomicInteger(0)

      sendMessage()

      receiveMessage(
        cf = cf,
        destName = "test",
        msgHandler = new JMSMessageHandler {
          override def handleMessage(msg: Message): Option[Throwable] = {
            count.incrementAndGet()
            None
          }
        }
      )

      count.get() should be (1)

      checkMessage(expectedMessageCount(0))
    }

    "should not consume messages if the message handler yields an exception" in {

      sendMessage()

      receiveMessage(
        cf = cf,
        destName = "test",
        msgHandler = new JMSMessageHandler {
          override def handleMessage(msg: Message): Option[Throwable] = {
            Some(new Exception("test failure"))
          }
        }
      )

      checkMessage(expectedMessageCount(1))
    }
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

  override protected def afterAll(): Unit = {
    broker.foreach { b =>
      b.stop()
      b.waitUntilStopped()
    }
  }
}
