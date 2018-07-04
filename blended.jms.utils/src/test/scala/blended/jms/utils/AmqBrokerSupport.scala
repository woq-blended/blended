package blended.jms.utils

import org.apache.activemq.ActiveMQConnectionFactory
import org.apache.activemq.broker.BrokerService
import org.apache.activemq.store.memory.MemoryPersistenceAdapter

trait AmqBrokerSupport {

  lazy val brokerName : String = "blended"
  def amqCf() = new ActiveMQConnectionFactory(s"vm://$brokerName?create=false")

  def startBroker() : Option[BrokerService] = {

    val b = new BrokerService()
    b.setBrokerName(brokerName)
    b.setPersistent(false)
    b.setUseJmx(false)
    b.setPersistenceAdapter(new MemoryPersistenceAdapter)

    b.start()
    b.waitUntilStarted()

    Some(b)
  }

  def stopBroker(broker: Option[BrokerService]) : Unit = {
    broker.foreach { b =>
      b.stop()
      b.waitUntilStopped()
    }
  }
}
