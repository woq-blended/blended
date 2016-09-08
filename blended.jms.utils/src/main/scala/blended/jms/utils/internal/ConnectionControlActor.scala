package blended.jms.utils.internal

import java.util.concurrent.TimeUnit
import javax.jms.{Connection, ConnectionFactory, Session}

import akka.actor.{Props, Actor, ActorLogging, Cancellable}
import akka.pattern.pipe
import blended.jms.utils.{BlendedSingleConnectionFactory, BlendedJMSConnectionConfig, BlendedJMSConnection}
import blended.mgmt.base.FrameworkService
import domino.service_consuming.ServiceConsuming
import org.osgi.framework.BundleContext

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.control.NonFatal

object ConnectionControlActor {

  def apply(
    provider: String, cf: ConnectionFactory, config: BlendedJMSConnectionConfig, bundleContext: BundleContext
  ) =
    new ConnectionControlActor(provider, cf, config, bundleContext)
}

class ConnectionControlActor(provider: String, cf: ConnectionFactory, config: BlendedJMSConnectionConfig, override val bundleContext: BundleContext)
  extends Actor with ActorLogging with ServiceConsuming {

  case object PingTimeout
  case class ConnectingTimeout(t: Long)
  case class PingResult(result : Either[Throwable, Boolean])
  case class ConnectResult(timestamp: Long, result: Either[Throwable, Connection])

  private[this] implicit val eCtxt = context.system.dispatcher
  private[this] var conn : Option[BlendedJMSConnection] = None

  private[this] val retrySchedule = config.retryInterval.seconds
  private[this] val schedule = Duration(config.pingInterval, TimeUnit.SECONDS)

  private[this] var firstConnectAttempt : Option[Long] = None
  private[this] var lastConnectAttempt: Long = 0l
  private[this] var pinging : Boolean = false
  private[this] var pingTimer : Option[Cancellable] = None

  private[this] var lastDisconnect : Option[Long] = None
  private[this] var failedPings : Int = 0

  override def preStart(): Unit = {
    log.debug(s"Initialising Connection controller [$provider]")
    self ! CheckConnection
  }

  override def preRestart(reason: Throwable, message: Option[Any]): Unit = {
    log.debug(s"Error encountered in ConnectionControlActor [${reason.getMessage}], restarting ...")
    super.preRestart(reason, message)
  }

  override def postStop(): Unit = {
    log.debug(s"Stopping Connection Control Actor for provider [$provider].")
    disconnect()
    pingTimer.foreach(_.cancel())
    super.postStop()
  }

  override def receive: Actor.Receive = disconnected

  def disconnected : Receive = {

    case PingResult(_) =>

    case CheckConnection =>
      pingTimer = None
      initConnection()

    case ConnectResult =>

  }

  def connected : Receive = {

    case CheckConnection =>
      pingTimer = None
      conn.foreach{ c => ping(c).pipeTo(self) }

    case PingResult(Right(_)) =>
      pinging = false
      failedPings = 0
      checkConnection(schedule)

    case PingResult(Left(t)) =>
      pinging = false
      log.debug(s"Error sending connection ping for provider [$provider].")
      checkReconnect()

    case PingTimeout =>
      if (pinging) {
        pinging = false
        log.debug(s"Ping for provider [$provider] timed out.")
        checkReconnect()
      }

    case ConnectResult =>

    case ConnectingTimeout =>

  }

  def connecting : Receive = {
    case PingResult(_) =>

    case CheckConnection =>
      pingTimer = None

    case ConnectResult(t, Left(e)) =>
      if (t == lastConnectAttempt) reconnect()

    case ConnectResult(t, Right(c)) =>
      if (t == lastConnectAttempt) {
        log.debug(s"Successfully connected to provider [$provider]")
        firstConnectAttempt = None
        conn = Some(new BlendedJMSConnection(c))
        publishConnection(conn)
        checkConnection(schedule)
        context.become(connected)
      }

    case ConnectingTimeout(t) =>
      if (t == lastConnectAttempt) reconnect()
  }

  def closing : Receive = {
    case PingResult(_) =>

    case CheckConnection =>
      pingTimer = None

    case ConnectionClosed =>
      conn = None
      publishConnection(None)
      lastDisconnect = Some(System.currentTimeMillis())
      checkConnection(schedule, true)
      context.become(disconnected)

    case CloseTimeout =>
      restartContainer(s"Unable to close connection for provider [${provider} in [${config.minReconnect}]s]. Restarting container ...")
      disconnect()
  }

  private[this] def initConnection() : Unit = {

    val remaining : Double = lastDisconnect match {
      case None => 0
      case Some(l) => config.minReconnect * 1000.0 - (System.currentTimeMillis() - l)
    }

    if (lastDisconnect.isDefined && remaining > 0) {
      log.debug(s"Container is waiting to reconnect, remaining wait time [${remaining / 1000.0}]s")
      checkConnection(schedule)
    } else {
      lastConnectAttempt = System.currentTimeMillis()
      connect(lastConnectAttempt).pipeTo(self)
      context.system.scheduler.scheduleOnce(30.seconds, self, ConnectingTimeout(lastConnectAttempt))
      context.become(connecting)
    }
  }

  private[this] def checkConnection(delay : FiniteDuration, force : Boolean = false) : Unit = {

    if (force) {
      pingTimer.foreach(_.cancel())
      pingTimer = None
    }

    if (pingTimer.isEmpty) {
      pingTimer = Some(context.system.scheduler.scheduleOnce(delay, self, CheckConnection))
    }
  }

  private[this] def connect(timestamp: Long) : Future[ConnectResult] = Future {
    try {
      log.debug(s"Creating connection to JMS provider [$provider]")
      if (config.maxReconnectTimeout > 0 && firstConnectAttempt.isEmpty && lastDisconnect.isDefined) {
        log.info(s"Starting max reconnect timeout monitor for provider [${provider}] with [${config.maxReconnectTimeout}]s")
        firstConnectAttempt = Some(lastConnectAttempt)
      }
      scala.concurrent.blocking {
        val connection = new BlendedJMSConnection(cf.createConnection())
        connection.start()
        ConnectResult(timestamp, Right(connection))
      }
    } catch {
      case NonFatal(e) =>
        log.debug(s"Error connecting to JMS provider [$provider]. ${e.getMessage()}")
        if (config.maxReconnectTimeout > 0 && firstConnectAttempt.isDefined) {
          firstConnectAttempt.foreach { t =>
            if ((System.currentTimeMillis() - t) / 1000l > config.maxReconnectTimeout) {
              restartContainer(s"Unable to reconnect to JMS provider [${provider}] in [${config.maxReconnectTimeout}]s. Restarting container ...")
            }
          }
        }
        ConnectResult(timestamp, Left(e))
    }
  }

  private[this] def disconnect() : Unit = {
    pingTimer.foreach(_.cancel())
    pingTimer = None
    failedPings = 0

    if (conn.isEmpty) {
      log.debug(s"Connection for provider is already disconnected [$provider]")
      context.become(disconnected)
    } else {
      log.debug(s"Closing connection for provider [$provider]")
      context.system.actorOf(Props(ConnectionCloseActor(conn.get.connection, config.minReconnect.seconds, self)))
      lastDisconnect = Some(System.currentTimeMillis())
      context.become(closing)
      publishConnection(None)
    }
  }

  private[this] def checkReconnect() : Unit = {
    failedPings = failedPings + 1
    if (failedPings == config.pingTolerance) {
      log.info("Maximum ping tolerance reached .... reconnecting.")
      reconnect()
    } else {
      checkConnection(retrySchedule)
    }
  }

  private[this] def reconnect() : Unit = {
    disconnect()
    checkConnection(retrySchedule)
  }

  private[this] def publishConnection(c: Option[Connection]) : Unit = BlendedSingleConnectionFactory.setConnection(provider, c)

  private[this] def restartContainer(msg: String) : Unit = {
    log.warning(msg)
    withService[FrameworkService, Unit] { _ match {
      case None =>
        log.warning("Could not find FrameworkServive to restart Container. Restarting through Framework Bundle ...")
        bundleContext.getBundle(0).update()
      case Some(s) => s.restartContainer(msg, true)
    }}

    context.stop(self)
  }

  private[this] def ping(c: Connection) : Future[PingResult] = {

    pinging = true
    context.system.scheduler.scheduleOnce(config.pingTimeout.seconds, self, PingTimeout)

    Future {
      var session: Option[Session] = None

      try {
        log.debug(s"Checking connection for provider [$provider]")
        session = Some(c.createSession(false, Session.AUTO_ACKNOWLEDGE))
        session.foreach { s =>
          val producer = s.createProducer(s.createTopic("blended.ping"))
          producer.send(s.createTextMessage(s"${System.currentTimeMillis()}"))
          producer.close()
        }
        PingResult(Right(true))
      } catch {
        case NonFatal(e) =>
          PingResult(Left(e))
      } finally {
        session.foreach(_.close())
      }
    }
  }
}
