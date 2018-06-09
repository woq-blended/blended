package blended.jms.utils.internal

import java.util.UUID
import java.util.concurrent.atomic.AtomicLong

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import blended.jms.utils.{BlendedJMSConnectionConfig, JMSSupport}
import javax.jms._

import scala.concurrent.duration._
import scala.util.control.NonFatal

private [internal] case class PingInfo(
  cfg: BlendedJMSConnectionConfig,
  started : Long,
  pingId : String,
  session : Option[Session],
  producer : Option[MessageProducer],
  consumer: Option[MessageConsumer],
  exception : Option[Throwable]
)

private [internal] trait PingOperations { this : JMSSupport =>

  private val log = org.log4s.getLogger

  def closeJmsResources(info: PingInfo) : Unit = {
    log.info(s"Closing JMS resources for [${info.cfg.vendor}:${info.cfg.provider}] with id [${info.pingId}]")
    try {
      info.consumer.foreach(_.close())
    } finally {
      try {
        info.producer.foreach(_.close())
      } finally {
        log.debug(s"closing session for [${info.cfg.vendor}:${info.cfg.provider}] with id [${info.pingId}]")
        info.session.foreach(_.close())
      }
    }
  }

  def initialisePing(con: Connection, config: BlendedJMSConnectionConfig) : PingInfo = {

    val pingId = UUID.randomUUID().toString()
    val timeOutMillis = config.pingTimeout.seconds.toMillis

    var session : Option[Session] = None
    var consumer : Option[MessageConsumer] = None
    var producer : Option[MessageProducer] = None

    try {
      val selector = s"""JMSCorrelationID='$pingId'"""

      session = Some(con.createSession(false, Session.AUTO_ACKNOWLEDGE))

      session.foreach { s =>
        val dest = destination(s, config.pingDestination)

        consumer = Some(s.createConsumer(dest, selector))
        producer = Some(s.createProducer(dest))

        val msg = s.createMessage()
        msg.setJMSCorrelationID(pingId)

        producer.foreach{ p =>
          p.send(msg, DeliveryMode.NON_PERSISTENT, 4, timeOutMillis * 2)
        }
      }

      log.debug(s"Sent ping message for [${config.vendor}:${config.provider}] with id [$pingId]")

      PingInfo(
        cfg = config,
        started = System.currentTimeMillis(),
        session = session,
        producer = producer,
        consumer = consumer,
        pingId = pingId,
        exception = None
      )
    } catch {
      case NonFatal(e) =>
        log.warn(s"Error initialising ping [${e.getMessage()}]")
        PingInfo(
          cfg = config,
          started = System.currentTimeMillis(),
          session = session,
          producer = producer,
          consumer = consumer,
          pingId = pingId,
          exception = Some(e)
        )
    }
  }

  def probePing(info : PingInfo) : Option[PingResult] = {

    log.debug(s"Probing ping for [${info.cfg.vendor}:${info.cfg.provider}] with id [${info.pingId}]")

    info.consumer match {
      case None => Some(PingResult(Left(new Exception(s"No consumer defined for [${info.cfg.vendor}:${info.cfg.provider}] and pingId [${info.pingId}]"))))
      case Some(c) =>
        try {
          Option(c.receive(100l)) match {
            case None =>
              None
            case Some(m) =>
              val id = m.getJMSCorrelationID()

              if (!info.pingId.equals(id)) {
                val msg = s"Received ping id [$id] for [${info.cfg.vendor}:${info.cfg.provider}] did not match expected is [$info.pingId]"
                log.debug(msg)
                Some(PingResult(Left(new Exception(msg))))
              }
              else
                log.debug(s"Ping successful for [${info.cfg.vendor}:${info.cfg.provider}] with id [${info.pingId}]")
                Some(PingResult(Right(info.pingId)))
          }
        } catch {
          case jmse : JMSException => Some(PingResult(Left(jmse)))
        }
    }
  }
}

private [internal] class DefaultPingOperations extends PingOperations with JMSSupport

object JmsPingPerformer {
  protected val counter : AtomicLong = new AtomicLong(0)

  def props(config: BlendedJMSConnectionConfig, con: Connection, operations : PingOperations) =
    Props(new JmsPingPerformer(config, con, operations))
}

class JmsPingPerformer(config: BlendedJMSConnectionConfig, con: Connection, operations: PingOperations)
  extends Actor with ActorLogging {

  implicit val eCtxt = context.system.dispatcher

  case object Tick

  override def receive: Receive = {
    case ExecutePing(pingActor) =>
      log.info(s"Executing ping for connection factory [${config.vendor}:${config.provider}]")

      val info = operations.initialisePing(con, config)

      info.exception match {
        case None =>
          log.debug(s"Successfully initialised ping for [${info.cfg.vendor}:${info.cfg.provider}] with id [${info.pingId}]")
          context.become(pinging(pingActor, info))
          self ! Tick
        case Some(t) =>
          log.debug(s"Failed to initialise ping for [${info.cfg.vendor}:${info.cfg.provider}] with id [${info.pingId}]")
          pingActor ! PingResult(Left(t))
          operations.closeJmsResources(info)
          context.stop(self)
      }
  }

  def pinging(pingActor : ActorRef, info: PingInfo) : Receive = {
    case Tick if (System.currentTimeMillis() >= info.started + info.cfg.pingTimeout.seconds.toMillis) =>
      context.stop(self)
      operations.closeJmsResources(info)

    case Tick =>
      operations.probePing(info) match {
        case None =>
          context.system.scheduler.scheduleOnce(100.millis, self, Tick)
        case Some(result) =>
          pingActor ! result
          operations.closeJmsResources(info)
      }

  }
}


