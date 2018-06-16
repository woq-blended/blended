package blended.jms.utils.internal

import java.lang.management.ManagementFactory

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import blended.jms.utils.{AmqBrokerSupport, BlendedJMSConnectionConfig, JMSSupport}
import javax.jms.Connection
import org.apache.activemq.broker.BrokerService
import org.scalatest.{BeforeAndAfterAll, FreeSpecLike}

import scala.concurrent.duration._

class JMSPingPerformerSpec extends TestKit(ActorSystem("JMSPingPerformer"))
  with FreeSpecLike
  with ImplicitSender
  with BeforeAndAfterAll
  with AmqBrokerSupport
  with JMSSupport {

  private[this] val cfg = BlendedJMSConnectionConfig.defaultConfig.copy(vendor = "amq", provider ="amq", clientId = "jmsPing")
  private[this] var broker : Option[BrokerService] = None

  private[this] def pingTest(
    con: Connection,
    cfg: BlendedJMSConnectionConfig,
    operations: PingOperations = new DefaultPingOperations()
  )(fish: PartialFunction[Any, Boolean]) : Option[Throwable] = {

    try {
      val testActor = system.actorOf(JmsPingPerformer.props(cfg, con, operations))
      val probe = TestProbe()

      testActor ! ExecutePing(probe.ref)
      probe.fishForMessage(cfg.pingTimeout.seconds)(fish)
      None
    } catch {
      case t : Throwable => Some(t)
    }
  }

  private[this] val pingSuccess : PartialFunction[Any, Boolean] = {
    case PingResult(Right(_)) => true
    case _ => false
  }

  private[this] val pingFailed : PartialFunction[Any, Boolean] = {
    case PingResult(Left(_)) => true
    case _ => false
  }

  private[this] val failingOps = new DefaultPingOperations() {
    override def initialisePing(con: Connection, config: BlendedJMSConnectionConfig): PingInfo = {
      super.initialisePing(con, config).copy(exception = Some(new Exception("failing")))
    }
  }

  private[this] var con : Option[Connection] = None

  "The JMSPingPerformer should " - {

    "perform a queue based ping" in {
      assert(pingTest(con.get, cfg.copy(clientId = "jmsPing", pingDestination = "queue:blendedPing"))(pingSuccess).isEmpty)
    }

    "perform a topic based ping" in {
      assert(pingTest(con.get, cfg.copy(clientId = "jmsPing", pingDestination = "topic:blendedPing"))(pingSuccess).isEmpty)
    }

    "respond with a negative ping result if the ping operation fails" in {
      assert(pingTest(
        con.get,
        cfg.copy(
          clientId = "jmsPing",
          pingDestination = "topic:blendedPing"
        ),
        failingOps
      )(pingFailed).isEmpty)
    }

    "does not leak threads on failed pings" in {

      def threadCount(): Int = ManagementFactory.getThreadMXBean().getThreadCount

      val base = threadCount()

      1.to(500).foreach { _ =>
        pingTest(
          con.get,
          cfg.copy(
            clientId = "jmsPing",
            pingDestination = "topic:blendedPing"
          ),
          failingOps
        )(pingFailed)
      }

      val current = threadCount()

      //Thread.sleep(3600000)
      assert(base - 10 <= current && current <= base + 10)
    }
  }

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
