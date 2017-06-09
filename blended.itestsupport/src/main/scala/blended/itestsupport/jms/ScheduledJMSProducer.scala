package blended.itestsupport.jms

import javax.jms.{ConnectionFactory, DeliveryMode, Message, Session}

import akka.actor.{Actor, ActorLogging, Props}
import blended.jms.utils.{JMSMessageFactory, JMSSupport}
import com.typesafe.config.Config
import scala.collection.JavaConverters._

import scala.concurrent.duration._

object ScheduledJMSProducer {

  def props(cf: ConnectionFactory, cfg: Config) = Props(new ScheduledJMSProducer(cf, cfg) with JMSMessageFactory[Long] {
    override def createMessage(session: Session, content: Long): Message = {
      val msg = session.createTextMessage(s"${content}")

      if (cfg.hasPath("properties")) {
        val props = cfg.getObject("properties").entrySet().asScala
        props.foreach { entry => msg.setStringProperty(entry.getKey(), cfg.getString("properties." + entry.getKey())) }
      }

      msg
    }
  })
}

class ScheduledJMSProducer(cf: ConnectionFactory, cfg: Config) extends Actor with ActorLogging with JMSSupport { this: JMSMessageFactory[Long] =>

  var counter : Long = 0

  implicit val eCtxt = context.system.dispatcher

  val schedule = cfg.getLong("interval")

  case object Tick

  override def preStart(): Unit = context.system.scheduler.scheduleOnce(schedule.millis, self, Tick)

  override def receive: Receive = {
    case Tick =>
      sendMessage(cf, cfg.getString("destination"), counter, this, DeliveryMode.PERSISTENT, 4, 0)
      context.system.scheduler.scheduleOnce(schedule.millis, self, Tick)
  }

}
