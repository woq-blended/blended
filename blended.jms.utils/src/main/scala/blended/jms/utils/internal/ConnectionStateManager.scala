package blended.jms.utils.internal

import java.text.SimpleDateFormat
import java.util.Date
import java.util.concurrent.TimeUnit
import javax.jms.Connection

import akka.actor.{Actor, ActorLogging, ActorRef, Cancellable, Props}
import blended.jms.utils.internal.ConnectionState._
import blended.jms.utils.{BlendedJMSConnection, BlendedJMSConnectionConfig}

import scala.concurrent.duration._

object ConnectionStateManager {

  def props(
    monitor: ActorRef, holder: ConnectionHolder, config: BlendedJMSConnectionConfig
  ) : Props =
    Props(new ConnectionStateManager(monitor, holder, config))
}

class ConnectionStateManager(monitor: ActorRef, holder: ConnectionHolder, config: BlendedJMSConnectionConfig)
  extends Actor with ActorLogging {

  type StateReceive = ConnectionState => Receive

  private[this] val df = new SimpleDateFormat("yyyyMMdd-HHmmss-SSS")

  private[this] implicit val eCtxt = context.system.dispatcher
  private[this] val provider = holder.provider

  private[this] var conn : Option[BlendedJMSConnection] = None

  private[this] var currentReceive : StateReceive = disconnected()
  private[this] var currentState : ConnectionState = ConnectionState()

  // the retry Schedule is the time interval we retry a connection after a failed connect attempt
  // usually that is only a fraction of the ping interval (i.e. 5 seconds)
  private[this] val retrySchedule = config.retryInterval.seconds

  // The schedule is the interval for the normal connection ping
  private[this] val schedule = Duration(config.pingInterval, TimeUnit.SECONDS)

  // The ping timer is used to schedule ping messages over the underlying connection to check it's
  // health
  private[this] var pingTimer : Option[Cancellable] = None

  // To this actor we delegate all connect and close operations for the underlying JMS provider
  private[this] val controller = context.actorOf(JmsConnectionController.props(holder))

  // If something causes an unexpected restart, we want to know
  override def preRestart(reason: Throwable, message: Option[Any]): Unit = {
    log.error(s"Error encountered in Connection State Manager [$provider] : [${reason.getMessage}], restarting ...")
    super.preRestart(reason, message)
  }

  // We clean up our JMS connections
  override def postStop(): Unit = {
    log.info(s"Stopping Connection State Manager for provider [$provider].")
    disconnect(currentState)
    super.postStop()
  }

  // The initial state is disconnected
  override def receive: Actor.Receive = Actor.emptyBehavior

  override def preStart(): Unit = {
    super.preStart()
    switchState(disconnected(), ConnectionState().copy(state = DISCONNECTED))
  }

  // ---- State: Disconnected
  def disconnected()(state: ConnectionState) : Receive = {

    // Upon a CheckConnection message we will kick off initiating and monitoring the connection
    case CheckConnection =>
      pingTimer = None
      initConnection(state)
  }

  // ---- State: Connected
  def connected()(state: ConnectionState) : Receive = {

    // If we are already connected we simply try to ping the underlying connection
    case CheckConnection =>
      pingTimer = None
      conn.foreach( ping )

    // For a successful ping we log the event and schedule the next connectionCheck
    case PingResult(Right(m)) =>
      switchState(
        connected(),
        publishEvents(state, s"JMS connection for provider [$provider] seems healthy [$m].").copy(failedPings = 0)
      )
      checkConnection(schedule)

    case PingResult(Left(t)) =>
      checkReconnect(
        publishEvents(state, s"Error sending connection ping for provider [$provider].")
          .copy(failedPings = state.failedPings + 1)
      )

    case PingTimeout =>
      checkReconnect(
        publishEvents(state, s"Ping for provider [$provider] timed out.")
          .copy(failedPings = state.failedPings + 1)
      )
  }

  // ---- State: Connecting
  def connecting()(state: ConnectionState) : Receive = {

    case CheckConnection =>
      pingTimer = None

    case ConnectResult(t, Left(e)) =>
      if (t == state.lastConnectAttempt.getOrElse(0l)) {
        switchState(disconnected(), state.copy(state = DISCONNECTED))
        if (!checkRestartForFailedReconnect(state, e)) {
          checkConnection(retrySchedule)
        }
      }

    // We successfully connected, record the connection and timestamps
    case ConnectResult(t, Right(c)) =>
      if (t == state.lastConnectAttempt.getOrElse(0l)) {
        conn = Some(new BlendedJMSConnection(c))
        checkConnection(schedule)
        switchState(connected(), publishEvents(state, s"Successfully connected to provider [$provider]").copy(
          state = CONNECTED,
          firstReconnectAttempt = None,
          lastConnect = Some(new Date())
        ))
      }

    case ConnectTimeout(t) =>
      if (t == state.lastConnectAttempt.getOrElse(0l)) {
        switchState(disconnected(), state.copy(state = DISCONNECTED))
        checkConnection(retrySchedule)
      }
  }

  // State: Closing
  def closing()(state: ConnectionState) : Receive = {

    case CheckConnection =>
      pingTimer = None

    // All good, happily disconnected
    case ConnectionClosed =>
      conn = None
      checkConnection(schedule, true)
      switchState(
        disconnected(),
        publishEvents(state, s"Connection for provider [$provider] successfully closed.")
          .copy(state = DISCONNECTED, lastDisconnect = Some(new Date()))
      )

    // Once we encounter a timeout for a connection close we initiate a Container Restart via the monitor
    case CloseTimeout =>
      val e = new Exception(s"Unable to close connection for provider [${provider} in [${config.minReconnect}]s]. Restarting container ...")
      monitor ! RestartContainer(e)
  }

  // helper methods

  // A convenience method to let us know which state we are switching to
  private[this] def switchState(rec: StateReceive, newState: ConnectionState) : Unit = {
    log.debug(s"Connection State Manager [$provider] switching to state [$newState]")
    currentReceive = rec
    currentState = newState
    monitor ! newState
    context.become(rec(newState).orElse(unhandled))
  }

  // A convenience method to capture unhandled messages
  def unhandled : Receive = {
    case m => log.debug(s"received unhandled message for [$provider] : ${m.toString()}")
  }

  // We simply stay in the same state and maintain the list of events
  def publishEvents(s : ConnectionState, msg: String*) : ConnectionState = {

    msg.foreach(m => log.info(m))
    val tsMsg = msg.map { m => df.format(new Date()) + " " + m }

    val newEvents = if (tsMsg.size >= s.maxEvents)
      tsMsg.reverse.take(s.maxEvents)
    else
      tsMsg.reverse ++ s.events.take(s.maxEvents - tsMsg.size)

    s.copy(events = newEvents.toList)
  }

  // To initialise the connection we check whether we have been connected at some point in the history of
  // this container has been started. If so, we will let not attempt to reconnect before the time specified
  // in the config has passed.
  // If not, we will try to connect immediately.

  private[this] def initConnection(s: ConnectionState) : Unit = {

    val remaining : Double = s.lastDisconnect match {
      case None => 0
      case Some(l) => config.minReconnect * 1000.0 - (System.currentTimeMillis() - l.getTime())
    }

    // if we were ever disconnected from the JMS provider since the container start we will check
    // whether the reconnect interval has passed, otherwise we will connect immediately
    if (s.lastDisconnect.isDefined && remaining > 0) {
      switchState(
        currentReceive,
        publishEvents(s, s"Container is waiting to reconnect for provider [${provider}], remaining wait time [${remaining / 1000.0}]s")
      )
      checkConnection(schedule)
    } else {
      switchState(connecting(), connect(s))
    }
  }

  // A simple convenience method to schedule the next connection check to ourselves
  private[this] def checkConnection(delay : FiniteDuration, force : Boolean = false) : Unit = {

    if (force) {
      pingTimer.foreach(_.cancel())
      pingTimer = None
    }

    if (pingTimer.isEmpty) {
      pingTimer = Some(context.system.scheduler.scheduleOnce(delay, self, CheckConnection))
    }
  }

  private[this] def connect(state: ConnectionState) : ConnectionState = {

    var events : List[String] = List(s"Creating connection to JMS provider [$provider]")

    val lastConnectAttempt = new Date()

    context.system.scheduler.scheduleOnce(30.seconds, self, ConnectTimeout(lastConnectAttempt.getTime()))

    // This only happens if we have configured a maximum reconnect timeout in the config and we ever
    // had a connection since this container was last restarted and we haven't started the timer yet
    val newState = if (config.maxReconnectTimeout > 0 && state.firstReconnectAttempt.isEmpty && state.lastDisconnect.isDefined) {
      events = (s"Starting max reconnect timeout monitor for provider [${provider}] with [${config.maxReconnectTimeout}]s") :: events
      state.copy(firstReconnectAttempt = Some(lastConnectAttempt))
    } else state

    controller ! Connect(lastConnectAttempt.getTime())

    // push the events into the newState in reverse order and set
    // the new state name
    publishEvents(newState, events.reverse.toArray:_*).copy(
      state = CONNECTING,
      lastConnectAttempt = Some(lastConnectAttempt)
    )
  }

  private[this] def checkRestartForFailedReconnect(s: ConnectionState, e: Throwable): Boolean = {

    var result = false

    log.info(s"Error connecting to JMS provider [$provider]. ${e.getMessage()}")

    if (config.maxReconnectTimeout > 0 && s.firstReconnectAttempt.isDefined) {
      s.firstReconnectAttempt.foreach { t =>
        if ((System.currentTimeMillis() - t.getTime()) / 1000l > config.maxReconnectTimeout) {
          val e = new Exception(s"Unable to reconnect to JMS provider [${provider}] in [${config.maxReconnectTimeout}]s. Restarting container ...")
          monitor ! RestartContainer(e)
          result = true
        }
      }
    }

    result
  }

  // Once we decided to close the connection we cancel our PingTimer
  // and start to cleanup. This goes into it's own state, so that we can
  // catch connections that cannot be closed within a reasonable time.
  // Experience has shown that for a close timeout it is best to restart
  // the container.
  private[this] def disconnect(s : ConnectionState) : Unit = {
    pingTimer.foreach(_.cancel())
    pingTimer = None

    // Notify the connection controller of the disconnect
    controller ! Disconnect(config.minReconnect.seconds)
    switchState(closing(), s.copy(state = CLOSING, failedPings = 0))
  }

  // A reconnect is only schedule if we have reached the maximumPingTolerance for the connection
  // Otherwise we schedule a connection check for the retry schedule, which is usually much shorter
  // than the normal connection check
  private[this] def checkReconnect(s: ConnectionState) : Unit = {
    if (s.failedPings == config.pingTolerance) {
      reconnect(
        publishEvents(s, s"Maximum ping tolerance for provider [$provider] reached .... reconnecting.")
      )
    } else {
      checkConnection(retrySchedule)
    }
  }

  private[this] def reconnect(s: ConnectionState) : Unit = {
    disconnect(s)
    checkConnection(retrySchedule)
  }

  private[this] def ping(c: Connection) : Unit = {
    log.info(s"Checking JMS connection for provider [$provider]")
    val pinger = context.actorOf(ConnectionPingActor.props(config.pingTimeout.seconds))
    val jmsPingPerformer = new JmsPingPerformer(pinger, provider, c, "blended.ping")
    pinger ! jmsPingPerformer
  }
}
