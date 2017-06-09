package blended.itestsupport.jms
import javax.jms.ConnectionFactory

import org.apache.activemq.ActiveMQConnectionFactory
import org.apache.activemq.broker.BrokerService
import org.apache.activemq.store.memory.MemoryPersistenceAdapter

object JMSTestDriverManual {

  val amqCf = new ActiveMQConnectionFactory("vm://blended?create=false")

  def main(args: Array[String]) : Unit = {

    val b = new BrokerService()
    b.setBrokerName("blended")
    b.setPersistent(false)
    b.setUseJmx(false)
    b.setPersistenceAdapter(new MemoryPersistenceAdapter())

    b.start()
    b.waitUntilStarted()

    new JMSTestDriver() {
      override val cf: ConnectionFactory = amqCf
    }.run()
  }

}
