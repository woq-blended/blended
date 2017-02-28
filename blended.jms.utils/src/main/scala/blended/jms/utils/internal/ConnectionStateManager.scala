package blended.jms.utils.internal

import java.util.concurrent.TimeUnit
import javax.jms.Connection

import akka.actor.{Actor, ActorLogging, ActorRef, Cancellable, Props}
import blended.jms.utils.{BlendedJMSConnection, BlendedJMSConnectionConfig, BlendedSingleConnectionFactory}

import scala.concurrent.duration._

object ConnectionStateManager {

  def props(
    monitor: ActorRef, holder: ConnectionHolder, config: BlendedJMSConnectionConfig
  ) : Props =
    Props(new ConnectionStateManager(monitor, holder, config))
}

class ConnectionStateManager(monitor: ActorRef, holder: ConnectionHolder, config: BlendedJMSConnectionConfig)
  extends Actor with ActorLogging {

  private[this] implicit val eCtxt = context.system.dispatcher
  private[this] val provider = holder.provider

  private[this] var state : String = "disconnected"

  private[this] var conn : Option[BlendedJMSConnection] = None

  // the retry Schedule is the time interval we retry a connection after a failed connect attempt
  // usually that is only a fraction of the ping interval (i.e. 5 seconds)
  private[this] val retrySchedule = config.retryInterval.seconds

  // The schedule is the interval for the normal connection ping
  private[this] val schedule = Duration(config.pingInterval, TimeUnit.SECONDS)

  // The firstReconnectAttempt, if defined holds the timestamp of the first reconnect attempt
  // and is used to measure the maximum Reconnect timeout
  private[this] var firstReconnectAttempt : Option[Long] = None

  // The last connect attempt holds the timestamp of the last connection attempt
  private[this] var lastConnectAttempt: Long = 0l

  // The ping timer is used to schedule ping messages over the underlying connection to check it's
  // health
  private[this] var pingTimer : Option[Cancellable] = None

  // lastDisconnect holds the timestamp when the last disconnect was detected and is used to
  // measure the time interval until a reconnect will be allowed
  private[this] var lastDisconnect : Option[Long] = None

  // For stability we allow a configurable number of pings to fail before we refresh the connection
  private[this] var failedPings : Int = 0

  // To this actor we delegate all connect and close operations for the underlying JMS provider
  private[this] val controller = context.actorOf(JmsConnectionController.props(holder))

  // If something causes an unexpected restart, we want to know
  override def preRestart(reason: Throwable, message: Option[Any]): Unit = {
    log.debug(s"Error encountered in ConnectionControlActor [${reason.getMessage}], restarting ...")
    super.preRestart(reason, message)
  }

  // We clean up our JMS connections
  override def postStop(): Unit = {
    log.debug(s"Stopping Connection Control Actor for provider [$provider].")
    disconnect()
    pingTimer.foreach(_.cancel())
    super.postStop()
  }

  // The initial state is disconnected
  override def receive: Actor.Receive = Actor.emptyBehavior

  override def preStart(): Unit = {
    super.preStart()
    switchState("disconnected", disconnected)
  }

  // ---- State: Disconnected
  def disconnected : Receive = {

    case PingResult(_) =>

    case CheckConnection =>
      pingTimer = None
      initConnection()

    case ConnectResult =>

  }

  // ---- State: Connected
  def connected : Receive = {

    case CheckConnection =>
      pingTimer = None
      conn.foreach( ping )

    case PingResult(Right(m)) =>
      log.info(s"JMS connection for provider [$provider] seems healthy [$m].")
      failedPings = 0
      checkConnection(schedule)

    case PingResult(Left(t)) =>
      log.debug(s"Error sending connection ping for provider [$provider].")
      checkReconnect()

    case PingTimeout =>
      log.warning(s"Ping for provider [$provider] timed out.")
      checkReconnect()

    case ConnectResult =>

    case ConnectTimeout =>
  }

  // ---- State: Connecting
  def connecting : Receive = {
    case PingResult(_) =>

    case CheckConnection =>
      pingTimer = None

    case ConnectResult(t, Left(e)) =>
      if (t == lastConnectAttempt) reconnect()

    case ConnectResult(t, Right(c)) =>
      if (t == lastConnectAttempt) {
        log.debug(s"Successfully connected to provider [$provider]")
        firstReconnectAttempt = None
        conn = Some(new BlendedJMSConnection(c))
        publishConnection(conn)
        checkConnection(schedule)
        switchState("connected", connected)
      }

    case ConnectTimeout(t) =>
      if (t == lastConnectAttempt) reconnect()
  }

  // State: Disconnecting
  def closing : Receive = {
    case PingResult(_) =>

    case CheckConnection =>
      pingTimer = None

    case ConnectionClosed =>
      conn = None
      publishConnection(None)
      lastDisconnect = Some(System.currentTimeMillis())
      checkConnection(schedule, true)
      switchState("disconnected", disconnected)

    case CloseTimeout =>
      val e = new Exception(s"Unable to close connection for provider [${provider} in [${config.minReconnect}]s]. Restarting container ...")
      monitor ! RestartContainer(e)
  }

  // helper methods

  // A convenience method to let us know which state we are switching to
  private[this] def switchState(name: String, rec: Receive) : Unit = {
    log.debug(s"Connection State Manager [$provider] switching to state [$name]")
    state = name
    context.become(rec.orElse(unhandled))
  }

  // A convenience method to capture unhandled messages
  def unhandled : Receive = {
    case m => log.debug(s"received unhandled message for [$provider] : ${m.toString()}")
  }

  // To initialise the connection we check whether we have been connected at some point in the history of
  // this container has been started. If so, we will let not attempt to reconnect before the time specified
  // in the config has passed.
  // If not, we will try to connect immediately.

  private[this] def initConnection() : Unit = {

    val remaining : Double = lastDisconnect match {
      case None => 0
      case Some(l) => config.minReconnect * 1000.0 - (System.currentTimeMillis() - l)
    }

    if (lastDisconnect.isDefined && remaining > 0) {
      log.debug(s"Container is waiting to reconnect for provider [${provider}], remaining wait time [${remaining / 1000.0}]s")
      checkConnection(schedule)
    } else {
      lastConnectAttempt = System.currentTimeMillis()
      connect(lastConnectAttempt)
      context.system.scheduler.scheduleOnce(30.seconds, self, ConnectTimeout(lastConnectAttempt))
      switchState("connecting", connecting)
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

  private[this] def connect(timestamp: Long) : Unit = {
    log.debug(s"Creating connection to JMS provider [$provider]")
    if (config.maxReconnectTimeout > 0 && firstReconnectAttempt.isEmpty && lastDisconnect.isDefined) {
      log.info(s"Starting max reconnect timeout monitor for provider [${provider}] with [${config.maxReconnectTimeout}]s")
      firstReconnectAttempt = Some(lastConnectAttempt)
    }

    controller ! Connect(timestamp)
  }

  private[this] def checkRestartForFailedReconnect(e: Throwable): Unit = {
    log.debug(s"Error connecting to JMS provider [$provider]. ${e.getMessage()}")
    if (config.maxReconnectTimeout > 0 && firstReconnectAttempt.isDefined) {
      firstReconnectAttempt.foreach { t =>
        if ((System.currentTimeMillis() - t) / 1000l > config.maxReconnectTimeout) {
          val e = new Exception(s"Unable to reconnect to JMS provider [${provider}] in [${config.maxReconnectTimeout}]s. Restarting container ...")
          monitor ! RestartContainer(e)
        }
      }
    }
  }

  private[this] def disconnect() : Unit = {
    pingTimer.foreach(_.cancel())
    pingTimer = None
    failedPings = 0

    controller ! Disconnect(config.minReconnect.seconds)
    switchState("closing", closing)
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


  private[this] def ping(c: Connection) : Unit = {
    log.info(s"Checking JMS connection for provider [$provider]")
    val pinger = context.actorOf(ConnectionPingActor.props(self, config.pingTimeout.seconds))
    val jmsPingPerformer = new JmsPingPerformer(pinger, provider, c, "blended.ping")
    pinger ! jmsPingPerformer
  }
}
