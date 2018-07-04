package blended.jms.utils.internal

import java.util.UUID
import java.util.concurrent.atomic.AtomicLong

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import blended.jms.utils.{BlendedJMSConnectionConfig, JMSSupport}
import javax.jms._

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._
import scala.util.Try
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
      info.session.foreach(_.close())
      log.debug(s"JMS session closed for [${info.cfg.vendor}:${info.cfg.provider}] with id [${info.pingId}]")
    } catch {
      case NonFatal(e) =>
        log.warn(s"Error closing session for [${info.cfg.vendor}:${info.cfg.provider}] with id [${info.pingId}]")
    }
  }

  def createSession(con: Connection) : Try[Session] =
    Try {con.createSession(false, Session.AUTO_ACKNOWLEDGE) }

  def createConsumer(s: Session, dest: String, selector: String) : Try[MessageConsumer] =
    Try { s.createConsumer(destination(s, dest), selector) }

  def createProducer(s: Session, dest: String) : Try[MessageProducer] =
    Try { s.createProducer(destination(s, dest)) }

  def initialisePing(con: Connection, config: BlendedJMSConnectionConfig, pingId: String)(implicit eCtxt: ExecutionContext) : Future[PingInfo] = Future {

    val timeOutMillis = config.pingTimeout.seconds.toMillis

    var session : Option[Session] = None
    var consumer : Option[MessageConsumer] = None
    var producer : Option[MessageProducer] = None

    try {
      val selector = s"""JMSCorrelationID='$pingId'"""

      session = Some(createSession(con).get)

      session.foreach { s =>
        log.debug(s"Session created for ping of [${config.vendor}:${config.provider}] and id [$pingId]")

        consumer = Some(createConsumer(s, config.pingDestination, selector).get)
        consumer.foreach { _ =>
          log.debug(s"Consumer created for ping of [${config.vendor}:${config.provider}] and id [$pingId]")
        }

        producer = Some(createProducer(s, config.pingDestination).get)
        producer.foreach { p =>
          log.debug(s"Producer created for ping of [${config.vendor}:${config.provider}] and id [$pingId]")

          val msg = s.createMessage()
          msg.setJMSCorrelationID(pingId)

          p.send(msg, DeliveryMode.NON_PERSISTENT, 4, timeOutMillis * 2)
          log.debug(s"Sent ping message for [${config.vendor}:${config.provider}] with id [$pingId]")
        }
      }

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

  def probePing(info : PingInfo)(implicit eCtxt: ExecutionContext) : Future[Option[PingResult]] = Future {

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
          case NonFatal(e) => Some(PingResult(Left(e)))
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

  implicit val eCtxt = context.system.dispatchers.lookup("FixedPool")

  private[this] val pingId = s"${config.vendor}:${config.provider}-${UUID.randomUUID().toString()}"

  case object Tick

  override def receive: Receive = {
    case ExecutePing(pingActor, id) =>
      log.info(s"Executing ping [$id] for connection factory [${config.vendor}:${config.provider}]")
      context.become(initializing(pingActor, id))
      operations.initialisePing(con, config, id.toString() + "-" + pingId).map(i => self ! i)
  }

  def initializing(pingActor: ActorRef, id: AnyVal) : Receive = {
    case info : PingInfo =>
      log.info(s"Received [$info]")
      info.exception match {
        case None =>
          log.debug(s"Successfully initialised ping for [${info.cfg.vendor}:${info.cfg.provider}] with id [${info.pingId}]")
          context.become(pinging(pingActor, id, info))
          self ! Tick
        case Some(t) =>
          log.debug(s"Failed to initialise ping for [${info.cfg.vendor}:${info.cfg.provider}] with id [${info.pingId}]")
          pingActor ! PingResult(Left(t))
          operations.closeJmsResources(info)
          context.stop(self)
      }
  }

  def pinging(pingActor : ActorRef, id: AnyVal, info: PingInfo) : Receive = {
    case Tick if (System.currentTimeMillis() >= info.started + info.cfg.pingTimeout.seconds.toMillis) =>
      operations.closeJmsResources(info)
      pingActor ! PingTimeout
      context.stop(self)

    case Tick =>
      operations.probePing(info).map {
        case None =>
          context.system.scheduler.scheduleOnce(100.millis, self, Tick)
        case Some(result) =>
          pingActor ! result
          operations.closeJmsResources(info)
          context.stop(self)
      }
  }
}


