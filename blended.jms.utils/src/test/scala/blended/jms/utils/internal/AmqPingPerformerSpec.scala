package blended.jms.utils.internal

import blended.jms.utils.{AmqBrokerSupport, BlendedJMSConnectionConfig}
import javax.jms.Connection
import org.apache.activemq.broker.BrokerService
import org.scalatest.BeforeAndAfterAll

class AmqPingPerformerSpec extends JMSPingPerformerSpec with BeforeAndAfterAll with AmqBrokerSupport {

  override var con: Option[Connection] = None
  private[this] var broker : Option[BrokerService] = None
  override val cfg = BlendedJMSConnectionConfig.defaultConfig.copy(vendor = "amq", provider ="amq", clientId = "jmsPing")

  override val bulkCount: Int = 100000

  override protected def beforeAll(): Unit = {
    broker = startBroker()
    con = Some(amqCf().createConnection())
    con.foreach(_.start())
  }

  override protected def afterAll(): Unit = {
    con.foreach(_.close())
    stopBroker(broker)
  }
}
