package blended.file

import akka.actor.ActorSystem
import blended.jms.utils.{IdAwareConnectionFactory, SimpleIdAwareConnectionFactory}
import org.apache.activemq.ActiveMQConnectionFactory
import org.apache.activemq.broker.BrokerService
import org.apache.activemq.store.memory.MemoryPersistenceAdapter

trait AmqBrokerSupport {

  def brokerName : String = "blended"

  var connF : IdAwareConnectionFactory = _

  private def amqCf()(implicit system : ActorSystem) : IdAwareConnectionFactory = SimpleIdAwareConnectionFactory(
    vendor = "amq",
    provider = "amq",
    clientId = "spec",
    cf = new ActiveMQConnectionFactory(s"vm://$brokerName?create=false&jms.prefetchPolicy.queuePrefetch=10")
  )

  def startBroker()(implicit system : ActorSystem) : BrokerService = {
    val b = new BrokerService()
    b.setBrokerName(brokerName)
    b.setPersistent(false)
    b.setUseJmx(false)
    b.setPersistenceAdapter(new MemoryPersistenceAdapter)
    b.setDedicatedTaskRunner(true)

    b.start()
    b.waitUntilStarted()

    connF = amqCf()
    b
  }

  def stopBroker(broker : Option[BrokerService]) : Unit = {
    broker.foreach { b =>
      b.stop()
      b.waitUntilStopped()
    }
  }
}
