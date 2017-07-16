package blended.streams.testapps

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import blended.jms.utils.BlendedSingleConnectionFactory
import org.apache.activemq.ActiveMQConnectionFactory
import org.apache.activemq.broker.BrokerService
import org.apache.activemq.store.memory.MemoryPersistenceAdapter

abstract class AbstractStreamRunner(s : String) {

  implicit val system = ActorSystem(s)
  implicit val materializer = ActorMaterializer()
  implicit val ec = system.dispatcher

  val broker : BrokerService = {

    val b = new BrokerService()
    b.setBrokerName("blended")
    b.setPersistent(false)
    b.setUseJmx(true)
    b.setPersistenceAdapter(new MemoryPersistenceAdapter)

    b.start()
    b.waitUntilStarted()

    b
  }

  val config = system.settings.config

  val cf = BlendedSingleConnectionFactory(
    cfg = config.getConfig("blended.activemq"),
    vendor = "activemq",
    provider = "activemq",
    cf = new ActiveMQConnectionFactory("vm://blended?create=false"),
    clientId = "JmsSender"
  )


}

