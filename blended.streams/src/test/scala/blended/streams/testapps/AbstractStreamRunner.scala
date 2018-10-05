package blended.streams.testapps

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import blended.jms.utils.{BlendedJMSConnectionConfig, BlendedSingleConnectionFactory}
import com.typesafe.config.ConfigFactory
import org.apache.activemq.ActiveMQConnectionFactory
import org.apache.activemq.broker.BrokerService
import org.apache.activemq.store.memory.MemoryPersistenceAdapter

import scala.collection.JavaConverters._

abstract class AbstractStreamRunner(s : String) {

  implicit val system = ActorSystem(s)
  implicit val materializer = ActorMaterializer()
  implicit val ec = system.dispatcher

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

  val config = ConfigFactory.parseMap(Map(
    "provider" -> "activemq",
    "clientId" -> "JmsSender",
    "properties.brokerURL" -> s"vm://blended?create=false"
  ).asJava)

  val cf = new BlendedSingleConnectionFactory(
    BlendedJMSConnectionConfig("activemq", config).copy(
      cfClassName = Some(classOf[ActiveMQConnectionFactory].getName)
    ),
    system,
    None
  )

}

