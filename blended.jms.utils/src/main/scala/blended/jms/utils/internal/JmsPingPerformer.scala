package blended.jms.utils.internal

import java.util.UUID
import java.util.concurrent.atomic.AtomicLong

import akka.actor.{Actor, ActorLogging, ActorRef, Cancellable, Props}
import akka.pattern.pipe
import blended.jms.utils.{ConnectionConfig, JMSSupport}
import blended.util.logging.Logger
import javax.jms._

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try
import scala.util.control.NonFatal

private[internal] case class PingInfo(
  cfg : ConnectionConfig,
  started : Long,
  pingId : String,
  session : Option[Session],
  producer : Option[MessageProducer],
  consumer : Option[MessageConsumer],
  exception : Option[Throwable]
)

//Todo: Migrate to JMS Stream Support
private[internal] trait PingOperations { this : JMSSupport =>

  private val log : Logger = Logger[PingOperations]
  private val receiveTimeout : Long = 100L

  def closeJmsResources(info : PingInfo)(implicit eCtxt : ExecutionContext) : Future[PingInfo] = Future {

    info.consumer.foreach { c =>
      // We can ignore the exception as closing the session should also close all consumers and producers
      try {
        c.close()
      } catch {
        case NonFatal(_) =>
          log.warn(s"Error closing consumer for [${info.cfg.vendor}:${info.cfg.provider}] with id [${info.pingId}]")
      }
    }

    info.producer.foreach { p =>
      try {
        p.close()
      } catch {
        case NonFatal(_) =>
          log.warn(s"Error closing producer for [${info.cfg.vendor}:${info.cfg.provider}] with id [${info.pingId}]")
      }
    }

    try {
      info.session.foreach { i =>
        log.info(s"Closing JMS session for [${info.cfg.vendor}:${info.cfg.provider}] with id [${info.pingId}]")
        i.close()
        log.debug(s"JMS session closed for [${info.cfg.vendor}:${info.cfg.provider}] with id [${info.pingId}]")
      }
      info.copy(session = None, consumer = None, producer = None)
    } catch {
      case NonFatal(_) =>
        log.warn(s"Error closing session for [${info.cfg.vendor}:${info.cfg.provider}] with id [${info.pingId}]")
        info
    }
  }

  def createSession(con : Connection) : Try[Session] =
    Try { con.createSession(false, Session.AUTO_ACKNOWLEDGE) }

  def createConsumer(s : Session, dest : String, selector : String) : Try[MessageConsumer] =
    Try { s.createConsumer(destination(s, dest), selector) }

  def createProducer(s : Session, dest : String) : Try[MessageProducer] =
    Try { s.createProducer(destination(s, dest)) }

  def initialisePing(con : Connection, config : ConnectionConfig, pingId : String)(implicit eCtxt : ExecutionContext) : Future[PingInfo] = Future {

    val timeOutMillis = config.pingTimeout.toMillis

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
        log.warn(s"Error initialising ping [${e.getMessage()}] for for [${config.vendor}:${config.provider}] with id [$pingId]")
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

  def probePing(info : PingInfo)(implicit eCtxt : ExecutionContext) : Future[PingResult] = Future {

    log.debug(s"Probing ping for [${info.cfg.vendor}:${info.cfg.provider}] with id [${info.pingId}]")

    info.consumer match {
      case None => PingFailed(new Exception(s"No consumer defined for [${info.cfg.vendor}:${info.cfg.provider}] and pingId [${info.pingId}]"))
      case Some(c) =>
        try {
          Option(c.receive(receiveTimeout)) match {
            case None => PingPending
            case Some(m) =>
              val id = m.getJMSCorrelationID()

              if (!info.pingId.equals(id)) {
                val msg = s"Received ping id [$id] for [${info.cfg.vendor}:${info.cfg.provider}] did not match expected is [$info.pingId]"
                log.debug(msg)
                PingFailed(new Exception(msg))
              } else {
                log.debug(s"Ping successful for [${info.cfg.vendor}:${info.cfg.provider}] with id [${info.pingId}]")
              }
              PingSuccess(info.pingId)
          }
        } catch {
          case NonFatal(e) => PingFailed(e)
        }
    }
  }
}

private[internal] class DefaultPingOperations extends PingOperations with JMSSupport

object JmsPingPerformer {
  protected val counter : AtomicLong = new AtomicLong(0)

  def props(config : ConnectionConfig, con : Connection, operations : PingOperations) : Props =
    Props(new JmsPingPerformer(config, con, operations))
}

class JmsPingPerformer(config : ConnectionConfig, con : Connection, operations : PingOperations)
  extends Actor with ActorLogging {

  implicit val eCtxt : ExecutionContext = context.system.dispatcher

  private[this] var pingId = s"${config.vendor}:${config.provider}-${UUID.randomUUID().toString()}"
  private[this] var responded : Boolean = false
  private[this] var pingInfo : Option[PingInfo] = None
  private[this] var timer : Option[Cancellable] = None

  case object Tick
  case object Timeout

  private[this] def respond(response : PingResult, pingActor : ActorRef) : Unit = {
    if (!responded) {
      responded = true
      log.info(s"Ping for [${config.vendor}:${config.provider}] with id [$pingId] yielded [$response].")
      timer.foreach(_.cancel())
      pingActor ! response
      pingInfo.foreach(i => self ! i)
      context.become(closing)
    }
  }

  override def receive : Receive = {
    case ExecutePing(pingActor, id) =>
      pingId = s"$id-$pingId"
      log.info(s"Executing ping [$pingId] for connection factory [${config.vendor}:${config.provider}]")
      timer = Some(context.system.scheduler.scheduleOnce(config.pingTimeout, self, Timeout))
      context.become(initializing(pingActor).orElse(timeoutHandler(pingActor)))
      operations.initialisePing(con, config, pingId).pipeTo(self)
  }

  def initializing(pingActor : ActorRef) : Receive = {
    case info : PingInfo =>
      log.debug(s"Received [$info]")

      pingInfo = Some(info)

      info.exception match {
        case None =>
          log.info(s"Successfully initialised ping for [${info.cfg.vendor}:${info.cfg.provider}] with id [$pingId]")
          context.become(pinging(pingActor).orElse(timeoutHandler(pingActor)))
          self ! Tick
        case Some(t) =>
          log.info(s"Failed to initialise ping for [${info.cfg.vendor}:${info.cfg.provider}] with id [$pingId]")
          respond(PingFailed(t), pingActor)
      }
  }

  def pinging(pingActor : ActorRef) : Receive = {
    case Tick =>
      pingInfo.foreach(i => operations.probePing(i).pipeTo(self))
    case PingPending =>
      context.system.scheduler.scheduleOnce(100.millis, self, Tick)
    case r : PingResult => respond(r, pingActor)
  }

  def closing : Receive = {
    case info : PingInfo =>
      pingInfo = Some(info)

      info.session match {
        case None =>
          context.stop(self)
        case Some(_) =>
          operations.closeJmsResources(info).pipeTo(self)
      }
  }

  def timeoutHandler(pingActor : ActorRef) : Receive = {
    case Timeout =>
      timer = None
      respond(PingTimeout, pingActor)
  }

  override def postStop() : Unit = {
    pingInfo.foreach(operations.closeJmsResources)
  }
}
