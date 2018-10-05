package blended.streams.testapps

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import blended.jms.utils.{BlendedJMSConnectionConfig, BlendedSingleConnectionFactory}
import org.apache.activemq.ActiveMQConnectionFactory
import org.apache.activemq.broker.BrokerService
import org.apache.activemq.store.memory.MemoryPersistenceAdapter

import scala.concurrent.ExecutionContext

abstract class AbstractStreamRunner(s : String) {

  implicit val system : ActorSystem = ActorSystem(s)
  implicit val materializer : ActorMaterializer = ActorMaterializer()
  implicit val ec : ExecutionContext = system.dispatcher

  def broker() : BrokerService = {

    val b = new BrokerService()
    b.setBrokerName("blended")
    b.setPersistent(false)
    b.setUseJmx(true)
    b.setPersistenceAdapter(new MemoryPersistenceAdapter)

    b.start()
    b.waitUntilStarted()

    b
  }

  val cf = new BlendedSingleConnectionFactory(
    BlendedJMSConnectionConfig.defaultConfig.copy(
      vendor = "activemq",
      provider =  "activemq",
      properties = Map("brokerURL" -> "vm://blended?create=false"),
      cfClassName = Some(classOf[ActiveMQConnectionFactory].getName)
    ),
    system,
    None
  )

}

