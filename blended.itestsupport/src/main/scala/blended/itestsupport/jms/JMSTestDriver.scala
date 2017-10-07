package blended.itestsupport.jms

import javax.jms.ConnectionFactory

import akka.actor.SupervisorStrategy.Restart
import akka.actor.{Actor, ActorLogging, ActorSystem, OneForOneStrategy, Props, SupervisorStrategy}
import blended.jms.utils.{BlendedJMSConnectionConfig, BlendedSingleConnectionFactory}
import com.typesafe.config.{Config, ConfigFactory}
import org.slf4j.LoggerFactory

import scala.collection.JavaConverters._

abstract class JMSTestDriver {

  val cf: ConnectionFactory

  private[this] val log = LoggerFactory.getLogger(classOf[JMSTestDriver])
  private[this] val system = ActorSystem("JMSTestDriver")

  def run() : Unit = {

    val jmsConfig = BlendedJMSConnectionConfig("unknown", ConfigFactory.parseMap(
      Map(
        "provider" -> "unknown",
        "clientId" -> "client"
      ).asJava
    )).copy(
      cfClassName = Some(cf.getClass.getName)
    )

    val config = ConfigFactory.load().getConfig(classOf[ScheduledJMSProducer].getName())
    system.actorOf(ProducerControlActor.props(new BlendedSingleConnectionFactory(
      config = jmsConfig,
      system = system,
      bundleContext = None
    )))
  }
}

object ProducerControlActor {

  def props(cf: ConnectionFactory) : Props = {
    Props(new ProducerControlActor(cf))
  }
}

class ProducerControlActor(cf: ConnectionFactory) extends Actor with ActorLogging {

  override def supervisorStrategy: SupervisorStrategy = OneForOneStrategy() {
    case _ => Restart
  }

  override def receive: Receive = Actor.emptyBehavior

  override def preStart(): Unit = {

    val cfgMap = context.system.settings.config.getObject(classOf[ScheduledJMSProducer].getName() + ".producer").entrySet().asScala

    cfgMap.foreach { entry =>
      log.info(s"Creating producer [${entry.getKey()}]")
      val cfg = context.system.settings.config.getConfig(classOf[ScheduledJMSProducer].getName() + ".producer." + entry.getKey())
      context.actorOf(ScheduledJMSProducer.props(cf, cfg))
    }

  }
}
