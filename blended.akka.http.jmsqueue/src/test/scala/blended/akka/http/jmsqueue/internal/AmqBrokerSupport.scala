package blended.akka.http.jmsqueue.internal

import akka.actor.ActorSystem
import blended.jms.utils.{IdAwareConnectionFactory, SimpleIdAwareConnectionFactory}
import org.apache.activemq.ActiveMQConnectionFactory
import org.apache.activemq.broker.BrokerService
import org.apache.activemq.store.memory.MemoryPersistenceAdapter

trait AmqBrokerSupport {

  lazy val brokerName : String = "blended"

  def amqCf()(implicit system : ActorSystem) : IdAwareConnectionFactory = {
    SimpleIdAwareConnectionFactory(
      vendor = "activemq",
      provider = "activemq",
      clientId = "spec",
      cf = new ActiveMQConnectionFactory(s"vm://$brokerName?create=false")
    )

  }

  def startBroker() : BrokerService = {

    val b = new BrokerService()
    b.setBrokerName(brokerName)
    b.setPersistent(false)
    b.setUseJmx(false)
    b.setPersistenceAdapter(new MemoryPersistenceAdapter)
    b.setDedicatedTaskRunner(true)

    b.start()
    b.waitUntilStarted()

    b
  }

  def stopBroker(broker : BrokerService) : Unit = {
    broker.stop()
    broker.waitUntilStopped()
  }
}
