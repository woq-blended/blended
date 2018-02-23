package blended.jms.utils.internal

import java.util.concurrent.atomic.AtomicLong

import javax.jms._
import akka.actor.ActorRef
import blended.jms.utils.{BlendedJMSConnectionConfig, JMSSupport}

import scala.concurrent.duration._
import scala.util.control.NonFatal
import scala.util.{Failure, Success, Try}

abstract class PingPerformer(pingActor: ActorRef, config: BlendedJMSConnectionConfig) {

  private[this] val log = org.log4s.getLogger

  val cfId = s"${config.vendor}:${config.provider}"

  final def ping() : Unit = {
    log.info(s"Performing ping for connection factory [$cfId]")
    new Thread(new Runnable {
      override def run(): Unit = doPing() match {
        case Success(s) =>
          log.info(s"Ping for connection factory [$cfId] succeeded with [$s]")
          pingActor ! PingReceived(s)
        case Failure(e) =>
          log.warn(s"Ping for connection factory [$cfId] failed [${e.getMessage()}]")
          pingActor ! PingResult(Left(e))
      }
    }).start()
  }

  protected def doPing() : Try[String]
}

object PingPerformer {
  val counter : AtomicLong = new AtomicLong(0)
}

class JmsPingPerformer(pingActor: ActorRef, config: BlendedJMSConnectionConfig, con: Connection)
  extends PingPerformer(pingActor, config) with JMSSupport {

  private[this] val log = org.log4s.getLogger

  override def doPing(): Try[String] = Try {

    val timeout = config.pingTimeout.seconds.toMillis
    val pingId = s"${config.clientId}--${PingPerformer.counter.getAndIncrement()}"

    def receive(consumer: MessageConsumer, tryUntil: Long) : Try[Option[Message]] = {
      try {
        Option(consumer.receiveNoWait()) match {
          case None =>
            Thread.sleep(500l)
            if (System.currentTimeMillis() < tryUntil)
              receive(consumer, tryUntil)
            else
              Success(None)
          case m @ Some(_) =>
            Success(m)
        }
      } catch {
        case NonFatal(e) => Failure(e)
      }
    }

    withSession { session =>
      val dest = destination(session, config.pingDestination)
      val consumer = session.createConsumer(dest, s"""JMSCorrelationID='$pingId'""")
      val producer = session.createProducer(dest)

      try {
        val msg = session.createMessage()
        msg.setJMSCorrelationID(config.clientId)

        producer.send(msg, DeliveryMode.NON_PERSISTENT, 4, timeout * 2)
        receive(consumer, System.currentTimeMillis() + timeout).get match {
          case None =>
            Some(new Exception(s"Ping receive [$pingId] timed out"))
          case Some(m) =>
            val id = m.getJMSCorrelationID()
            if (!pingId.equals(id))
              Some(new Exception(s"Received ping id [$id] did not match expected is [$pingId]"))
            else
              None
        }
      } finally {
        consumer.close()
        producer.close()
      }
    } (con) match {
      case Some(t) => throw t
      case None => pingId
    }
  }
}

