package blended.jms.utils.internal

import java.util.concurrent.atomic.AtomicLong

import javax.jms._
import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import blended.jms.utils.{BlendedJMSConnectionConfig, JMSSupport}

import scala.concurrent.duration._
import scala.util.control.NonFatal

object JmsPingPerformer {
  protected val counter : AtomicLong = new AtomicLong(0)

  def props(config: BlendedJMSConnectionConfig, con: Connection) =
    Props(new JmsPingPerformer(config, con))
}

class JmsPingPerformer(config: BlendedJMSConnectionConfig, con: Connection)
  extends Actor with ActorLogging with JMSSupport {

  val timeOutMillis = config.pingTimeout.seconds.toMillis
  implicit val eCtxt = context.system.dispatcher

  case object Tick

  override def receive: Receive = {
    case ExecutePing(pingActor) =>
      val pingId = s"${config.clientId}--${JmsPingPerformer.counter.getAndIncrement()}"

      try {
        val session = con.createSession(false, Session.AUTO_ACKNOWLEDGE)
        val dest = destination(session, config.pingDestination)
        val consumer = session.createConsumer(dest, s"""JMSCorrelationID='$pingId'""")
        val producer = session.createProducer(dest)

        val msg = session.createMessage()
        msg.setJMSCorrelationID(config.clientId)

        producer.send(msg, DeliveryMode.NON_PERSISTENT, 4, timeOutMillis * 2)
        producer.close()

        context.become(pinging(pingActor, System.currentTimeMillis(), session, consumer, pingId))
        self ! Tick
      } catch {
        case NonFatal(e) =>
          pingActor ! PingResult(Left(e))
          context.stop(self)
      }
  }

  def pinging(pingActor : ActorRef, started : Long, session: Session, consumer: MessageConsumer, pingId: String) : Receive = {
    case Tick if (System.currentTimeMillis() >= started + timeOutMillis) =>
      context.stop(self)
      consumer.close()
      session.close()

    case Tick =>
      Option(consumer.receive(100l)) match {
        case None =>
          context.system.scheduler.scheduleOnce(100.millis, self, Tick)
        case Some(m) =>
          val id = m.getJMSCorrelationID()

          consumer.close()
          session.close()

          if (!pingId.equals(id))
            pingActor ! PingResult(Left(new Exception(s"Received ping id [$id] did not match expected is [$pingId]")))
          else
            pingActor ! PingResult(Right(pingId))
      }
  }
}


