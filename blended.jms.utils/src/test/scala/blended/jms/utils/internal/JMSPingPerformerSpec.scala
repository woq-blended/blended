package blended.jms.utils.internal

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import blended.jms.utils.{AmqBrokerSupport, BlendedJMSConnectionConfig, JMSSupport}
import org.scalatest.{BeforeAndAfterAll, FreeSpecLike}

import scala.concurrent.duration._

class JMSPingPerformerSpec extends TestKit(ActorSystem("JMSPingPerformer"))
  with FreeSpecLike
  with ImplicitSender
  with BeforeAndAfterAll
  with AmqBrokerSupport
  with JMSSupport {

  private[this] val cfg = BlendedJMSConnectionConfig.defaultConfig.copy(vendor = "amq", provider ="amq")

  private[this] def pingTest(cfg: BlendedJMSConnectionConfig) : Option[Throwable] = {
    withConnection { con =>

      val jmsCfg = BlendedJMSConnectionConfig.defaultConfig.copy(vendor = "amq", provider = "amq", clientId = "jmsPing")

      val testActor = system.actorOf(JmsPingPerformer.props(jmsCfg, con, new DefaultPingOperations()))
      val probe = TestProbe()

      testActor ! ExecutePing(probe.ref)
      probe.fishForMessage(jmsCfg.pingTimeout.seconds) {
        case PingResult(Right(_)) => true
        case _ => false
      }
    } (amqCf)
  }

  "The JMSPingPerformer should " - {

    "perform a queue based ping" in {
      assert(pingTest(cfg.copy(clientId = "jmsPing", pingDestination = "queue:blended.ping")).isEmpty)
    }

    "perform a topic based ping" in {
      assert(pingTest(cfg.copy(clientId = "jmsPing", pingDestination = "topic:blended.ping")).isEmpty)
    }
  }

  override protected def beforeAll(): Unit = startBroker()

  override protected def afterAll(): Unit = stopBroker()

}
