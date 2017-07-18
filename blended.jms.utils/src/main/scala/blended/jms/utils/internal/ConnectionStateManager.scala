package blended.jms.utils.internal

import java.text.SimpleDateFormat
import java.util.Date
import java.util.concurrent.TimeUnit
import javax.jms.Connection

import akka.actor.{Actor, ActorLogging, ActorRef, Cancellable, Props}
import akka.event.LoggingReceive
import blended.jms.utils.internal.ConnectionState._
import blended.jms.utils.{BlendedJMSConnection, BlendedJMSConnectionConfig}
import com.typesafe.config.Config

import scala.concurrent.duration._

object ConnectionStateManager {

  def props(
    cfg: Config,
    monitor: ActorRef,
    holder: ConnectionHolder,
    clientId : String
  ) : Props =
    Props(new ConnectionStateManager(cfg, monitor, holder, clientId))
}

class ConnectionStateManager(cfg: Config, monitor: ActorRef, holder: ConnectionHolder, clientId: String)
  extends Actor with ActorLogging {

  type StateReceive = ConnectionState => Receive

  private[this] val config = BlendedJMSConnectionConfig(cfg)
  private[this] val df = new SimpleDateFormat("yyyyMMdd-HHmmss-SSS")

  private[this] implicit val eCtxt = context.system.dispatcher
  private[this] val provider = holder.provider
  private[this] val vendor = holder.vendor

  private[this] var conn : Option[BlendedJMSConnection] = None

  private[this] var currentReceive : StateReceive = disconnected()
  private[this] var currentState : ConnectionState = ConnectionState(provider = holder.provider).copy(status = DISCONNECTED)

  private[this] var pinger : Option[ActorRef] = None

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
    switchState(disconnected(), currentState)
    context.system.eventStream.subscribe(self, classOf[ConnectionCommand])
  }

  // ---- State: Disconnected
  def disconnected()(state: ConnectionState) : Receive = LoggingReceive {

    // Upon a CheckConnection message we will kick off initiating and monitoring the connection
    case cc : CheckConnection =>
      pingTimer = None
      initConnection(state, cc.now)
  }

  // ---- State: Connected
  def connected()(state: ConnectionState) : Receive = {

    // we simply eat up the CloseTimeOut messages that might still be going for previous
    // connect attempts
    case ConnectTimeout(_) => // do nothing, this will just get rid of unrelevant warnings in the log

    // If we are already connected we simply try to ping the underlying connection
    case cc : CheckConnection =>
      pingTimer = None
      conn.foreach( ping )

    // For a successful ping we log the event and schedule the next connectionCheck
    case PingResult(Right(m)) =>
      pinger = None
      switchState(
        connected(),
        publishEvents(state, s"JMS connection for provider [$vendor:$provider] seems healthy [$m].").copy(failedPings = 0)
      )
      checkConnection(schedule)

    case PingResult(Left(t)) =>
      pinger = None

      checkReconnect(
        publishEvents(state, s"Error sending connection ping for provider [$vendor:$provider].")
          .copy(failedPings = state.failedPings + 1)
      )

    case PingTimeout =>
      pinger = None

      checkReconnect(
        publishEvents(state, s"Ping for provider [$vendor:$provider] timed out.")
          .copy(failedPings = state.failedPings + 1)
      )
  }

  // ---- State: Connecting
  def connecting()(state: ConnectionState) : Receive = {

    case cc : CheckConnection =>
      pingTimer = None

    case ConnectResult(t, Left(e)) =>
      if (t == state.lastConnectAttempt.getOrElse(0l)) {
        switchState(disconnected(), state.copy(status = DISCONNECTED))
        if (!checkRestartForFailedReconnect(state, e)) {
          checkConnection(retrySchedule)
        }
      }

    // We successfully connected, record the connection and timestamps
    case ConnectResult(t, Right(c)) =>
      if (t == state.lastConnectAttempt.getOrElse(0l)) {
        conn = Some(new BlendedJMSConnection(c))
        checkConnection(schedule)
        switchState(connected(), publishEvents(state, s"Successfully connected to provider [$vendor:$provider]").copy(
          status = CONNECTED,
          firstReconnectAttempt = None,
          lastConnect = Some(new Date()),
          failedPings = 0
        ))
      }

    case ConnectTimeout(t) =>
      if (t == state.lastConnectAttempt.getOrElse(0l)) {
        switchState(disconnected(), state.copy(status = DISCONNECTED))
        checkConnection(retrySchedule)
      }
  }

  // State: Closing
  def closing()(state: ConnectionState) : Receive = {

    case cc : CheckConnection =>
      pingTimer = None

    // All good, happily disconnected
    case ConnectionClosed =>
      conn = None
      checkConnection(schedule, true)
      switchState(
        disconnected(),
        publishEvents(state, s"Connection for provider [$vendor:$provider] successfully closed.")
          .copy(status = DISCONNECTED, lastDisconnect = Some(new Date()))
      )

    // Once we encounter a timeout for a connection close we initiate a Container Restart via the monitor
    case CloseTimeout =>
      val e = new Exception(s"Unable to close connection for provider [$vendor:$provider] in [${config.minReconnect}]s]. Restarting container ...")
      monitor ! RestartContainer(e)
  }

  def jmxOperations(state : ConnectionState) : Receive = {
    case cmd : ConnectionCommand =>
      if (cmd.provider == provider) {
        if (cmd.disconnectPending)
          disconnect(state)
        else if (cmd.connectPending)
          self ! CheckConnection(cmd.reconnectNow)
      }
  }

  // helper methods

  // A convenience method to let us know which state we are switching to
  private[this] def switchState(rec: StateReceive, newState: ConnectionState) : Unit = {

    val nextState = publishEvents(newState, s"Connection State Manager [$vendor:$provider] switching to state [${newState.status}]")
    currentReceive = rec
    currentState = nextState
    monitor ! ConnectionStateChanged(nextState)
    context.become(LoggingReceive (rec(nextState).orElse(jmxOperations(nextState)).orElse(unhandled)) )
  }

  // A convenience method to capture unhandled messages
  def unhandled : Receive = {
    case m => log.debug(s"received unhandled message for [$vendor:$provider] : ${m.toString()}")
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

  private[this] def initConnection(s: ConnectionState, now : Boolean) : Unit = {

    val remaining : Double = s.lastDisconnect match {
      case None => 0
      case Some(l) => config.minReconnect * 1000.0 - (System.currentTimeMillis() - l.getTime())
    }

    // if we were ever disconnected from the JMS provider since the container start we will check
    // whether the reconnect interval has passed, otherwise we will connect immediately
    if (!now && s.lastDisconnect.isDefined && remaining > 0) {
      switchState(
        currentReceive,
        publishEvents(s, s"Container is waiting to reconnect for provider [$vendor:$provider], remaining wait time [${remaining / 1000.0}]s")
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
      pingTimer = Some(context.system.scheduler.scheduleOnce(delay, self, CheckConnection(false)))
    }
  }

  private[this] def connect(state: ConnectionState) : ConnectionState = {

    var events : List[String] = List(s"Creating connection to JMS provider [$vendor:$provider]")

    val lastConnectAttempt = new Date()

    context.system.scheduler.scheduleOnce(30.seconds, self, ConnectTimeout(lastConnectAttempt.getTime()))

    // This only happens if we have configured a maximum reconnect timeout in the config and we ever
    // had a connection since this container was last restarted and we haven't started the timer yet
    val newState = if (config.maxReconnectTimeout > 0 && state.firstReconnectAttempt.isEmpty && state.lastDisconnect.isDefined) {
      events = (s"Starting max reconnect timeout monitor for provider [$vendor:$provider] with [${config.maxReconnectTimeout}]s") :: events
      state.copy(firstReconnectAttempt = Some(lastConnectAttempt))
    } else state

    controller ! Connect(lastConnectAttempt, clientId)

    // push the events into the newState in reverse order and set
    // the new state name
    publishEvents(newState, events.reverse.toArray:_*).copy(
      status = CONNECTING,
      lastConnectAttempt = Some(lastConnectAttempt)
    )
  }

  private[this] def checkRestartForFailedReconnect(s: ConnectionState, e: Throwable): Boolean = {

    var result = false

    log.error(e, s"Error connecting to JMS provider [$vendor:$provider].")

    if (config.maxReconnectTimeout > 0 && s.firstReconnectAttempt.isDefined) {
      s.firstReconnectAttempt.foreach { t =>
        if ((System.currentTimeMillis() - t.getTime()) / 1000l > config.maxReconnectTimeout) {
          val e = new Exception(s"Unable to reconnect to JMS provider [$vendor:$provider] in [${config.maxReconnectTimeout}]s. Restarting container ...")
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
    switchState(closing(), s.copy(status = CLOSING))
  }

  // A reconnect is only schedule if we have reached the maximumPingTolerance for the connection
  // Otherwise we schedule a connection check for the retry schedule, which is usually much shorter
  // than the normal connection check
  private[this] def checkReconnect(s: ConnectionState) : Unit = {
    log.debug(s"Checking reconnect for provider [$vendor:$provider] state [$s] against tolerance [${config.pingTolerance}]")
    if (s.failedPings == config.pingTolerance) {
      reconnect(
        publishEvents(s, s"Maximum ping tolerance for provider [$vendor:$provider] reached .... reconnecting.")
      )
    } else {
      switchState(currentReceive, s)
      checkConnection(retrySchedule)
    }
  }

  private[this] def reconnect(s: ConnectionState) : Unit = {
    disconnect(s)
    checkConnection(retrySchedule)
  }

  private[this] def ping(c: Connection) : Unit = {

    pinger match {
      case None =>
        log.info(s"Checking JMS connection for provider [$vendor:$provider]")
        pinger = Some(context.actorOf(ConnectionPingActor.props(config.pingTimeout.seconds)))

        pinger.foreach { p =>
          val jmsPingPerformer = new JmsPingPerformer(p, provider, c, "blended.ping")
          p ! jmsPingPerformer
        }
      case Some(a) =>
    }
  }
}
