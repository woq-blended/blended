package blended.jms.utils.internal

import java.util.concurrent.TimeUnit
import javax.jms.{Connection, ConnectionFactory, Session}

import akka.actor.{Props, Actor, ActorLogging, Cancellable}
import akka.pattern.pipe
import blended.jms.utils.{BlendedJMSConnectionConfig, BlendedJMSConnection}
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

  private[this] var lastConnect: Long = 0l
  private[this] var pinging : Boolean = false
  private[this] var pingTimer : Option[Cancellable] = None

  private[this] var lastDisconnect : Long = 0l
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

    case GetConnection =>
      sender() ! conn
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

    case GetConnection =>
      sender() ! conn

    case ConnectResult =>

    case ConnectingTimeout =>

  }

  def connecting : Receive = {
    case PingResult(_) =>

    case CheckConnection =>
      pingTimer = None

    case GetConnection =>
      sender() ! conn

    case ConnectResult(t, Left(e)) =>
      if (t == lastConnect) reconnect()

    case ConnectResult(t, Right(c)) =>
      if (t == lastConnect) {
        log.debug(s"Successfully connected to provider [$provider]")
        conn = Some(new BlendedJMSConnection(c))
        pingTimer = Some(context.system.scheduler.scheduleOnce(schedule, self, CheckConnection))
        context.become(connected)
      }

    case ConnectingTimeout(t) =>
      if (t == lastConnect) reconnect()
  }

  def closing : Receive = {
    case PingResult(_) =>

    case CheckConnection =>
      pingTimer = None

    case GetConnection =>
      log.debug(s"Container is still disconnecting from JMS provider [$provider]")
      None

    case ConnectionClosed =>
      conn = None
      context.become(disconnected)

    case CloseTimeout =>
      val msg = s"Unable to close connection for provider [${provider} in [${config.minReconnect}]s]. Restarting container ..."
      log.warning(msg)
      withService[FrameworkService, Unit] { _ match {
        case None =>
          log.warning("Could not find FrameworkServive to restart Container. Restarting through Framework Bundle ...")
          bundleContext.getBundle(0).update()
        case Some(s) => s.restartContainer(msg)
      }}
      disconnect()
  }

  private[this] def initConnection() : Unit = {
    val remaining : Double = config.minReconnect * 1000.0 - (System.currentTimeMillis() - lastConnect)
    if (remaining > 0) {
      log.debug(s"Container is waiting to reconnect, remaining wait time [${remaining / 1000.0}]s")
      checkConnection(schedule)
    } else {
      lastConnect = System.currentTimeMillis()
      connect(lastConnect).pipeTo(self)
      context.system.scheduler.scheduleOnce(30.seconds, self, ConnectingTimeout(lastConnect))
      context.become(connecting)
    }
  }

  private[this] def checkConnection(delay : FiniteDuration) : Unit = {
    if (pingTimer.isEmpty) {
      pingTimer = Some(context.system.scheduler.scheduleOnce(delay, self, CheckConnection))
    }
  }

  private[this] def connect(timestamp: Long) : Future[ConnectResult] = Future {
    try {
      log.debug(s"Creating connection to JMS provider [$provider]")
      scala.concurrent.blocking {
        val connection = new BlendedJMSConnection(cf.createConnection())
        connection.start()
        ConnectResult(timestamp, Right(connection))
      }
    } catch {
      case NonFatal(e) =>
        log.debug(s"Error connecting to JMS provider [$provider]. ${e.getMessage()}")
        ConnectResult(timestamp, Left(e))
    }
  }

  private[this] def disconnect() : Unit = {
    pingTimer.foreach(_.cancel())
    pingTimer = None
    failedPings = 0

    conn.foreach { c =>
      log.debug(s"Closing connection for provider [$provider]")
      context.system.actorOf(Props(ConnectionCloseActor(c.connection, config.minReconnect.seconds, self)))
    }
    context.become(closing)
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
