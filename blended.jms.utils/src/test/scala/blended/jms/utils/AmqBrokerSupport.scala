package blended.jms.utils

import org.apache.activemq.ActiveMQConnectionFactory
import org.apache.activemq.broker.BrokerService
import org.apache.activemq.store.memory.MemoryPersistenceAdapter

trait AmqBrokerSupport {

  lazy val brokerName : String = "blended"

  var broker : Option[BrokerService] = None

  val amqCf = new ActiveMQConnectionFactory(s"vm://$brokerName?create=false")

  def startBroker() : Unit = {

    broker = {
      val b = new BrokerService()
      b.setBrokerName(brokerName)
      b.setPersistent(false)
      b.setUseJmx(false)
      b.setPersistenceAdapter(new MemoryPersistenceAdapter)

      b.start()
      b.waitUntilStarted()

      Some(b)
    }
  }

  def stopBroker() : Unit = {
    broker.foreach { b =>
      b.stop()
      b.waitUntilStopped()
    }
  }
}
