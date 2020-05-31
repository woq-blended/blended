package blended.jms.utils.internal

import java.text.SimpleDateFormat
import java.util.Date
import java.util.concurrent.atomic.AtomicLong

import akka.actor.{Actor, ActorRef, Cancellable, Props, Terminated}
import blended.jms.utils.internal.ConnectionState._
import blended.jms.utils.{BlendedJMSConnection, ConnectionConfig, Reconnect}
import blended.util.logging.Logger
import javax.jms.Connection

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

object ConnectionStateManager {

  def props(
    config: ConnectionConfig,
    monitor: ActorRef,
    holder: ConnectionHolder
  ) : Props =
    Props(new ConnectionStateManager(config, monitor, holder))
}

class ConnectionStateManager(config: ConnectionConfig, monitor: ActorRef, holder: ConnectionHolder)
  extends Actor {

  type StateReceive = ConnectionState => Receive

  private val df = new SimpleDateFormat("yyyyMMdd-HHmmss-SSS")

  implicit val eCtxt : ExecutionContext = context.system.dispatcher
  private val provider : String = config.provider
  private val vendor : String = config.vendor
  private val log : Logger = Logger(s"${getClass().getName()}.$vendor.$provider")

  private var conn : Option[BlendedJMSConnection] = None

  private var currentReceive : StateReceive = disconnected()
  private[internal] var currentState : ConnectionState = ConnectionState(provider = config.provider).copy(status = DISCONNECTED)

  private val pingCounter = new AtomicLong(0)
  private var pinger : Option[ActorRef] = None

  // the retry Schedule is the time interval we retry a connection after a failed connect attempt
  // usually that is only a fraction of the ping interval (i.e. 5 seconds)
  private val retrySchedule : FiniteDuration = config.retryInterval

  // The schedule is the interval for the normal connection ping
  private val schedule : FiniteDuration = config.pingInterval

  // The ping timer is used to schedule ping messages over the underlying connection to check it's
  // health
  private var pingTimer : Option[Cancellable] = None

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
    context.system.eventStream.subscribe(self, classOf[Reconnect])
  }

  // ---- State: Disconnected
  def disconnected()(state: ConnectionState) : Receive = {

    // Upon a CheckConnection message we will kick off initiating and monitoring the connection
    case cc : CheckConnection =>
      pingTimer = None
      log.debug(s"Trying to initialize connection [$vendor:$provider]")
      initConnection(state, cc.now)
  }

  // ---- State: Connected
  def connected()(state: ConnectionState) : Receive = {

    // we simply eat up the CloseTimeOut messages that might still be going for previous
    // connect attempts
    case ConnectTimeout(_) => // do nothing, this will just get rid of irrelevant warnings in the log

    // If we are already connected we simply try to ping the underlying connection
    case cc : CheckConnection =>
      pingTimer = None
      conn.foreach( ping )

    case Disconnect(_) => disconnect(state)

    // For a successful ping we log the event and schedule the next connectionCheck
    case PingSuccess(m) =>
      pinger = None
      switchState(
        connected(),
        publishEvents(state, s"JMS connection for provider [$vendor:$provider] seems healthy [$m].").copy(failedPings = 0)
      )
      checkConnection(schedule)

    case PingFailed(t) =>
      pinger = None

      checkReconnect(
        publishEvents(state, s"Error sending connection ping for provider [$vendor:$provider] : [${t.getMessage()}]. (failed pings = [${state.failedPings + 1} / ${config.pingTolerance}])")
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

    case _ : CheckConnection =>
      pingTimer = None

    case ConnectResult(t, Left(e)) =>
      if (t == state.lastConnectAttempt.getOrElse(0l)) {
        state.controller.foreach(context.system.stop)
        switchState(disconnected(), stopController(state).copy(status = DISCONNECTED))
        if (!checkRestartForFailedReconnect(state, Some(e))) {
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
          // We clear the firstReconnect timestamp, so that we get a fresh one later on
          firstReconnectAttempt = None,
          lastConnect = Some(new Date()),
          failedPings = 0
        ))
      }

    case ConnectTimeout(t) =>
      log.debug(s"Encountered Connection timeout (connectAttempt:$t), firstConnectAttempt: ${state.firstReconnectAttempt}, lastConnectAttempt: ${state.lastConnectAttempt}")
      if (!checkRestartForFailedReconnect(state, None)) {
        switchState(connecting(), connect(state))
      }
  }

  // State: Closing
  def closing()(state: ConnectionState) : Receive = {

    case _ : CheckConnection =>
      pingTimer = None

    // All good, happily disconnected
    case ConnectionClosed =>
      conn = None
      checkConnection(config.minReconnect, true)
      state.controller.foreach(context.system.stop)
      switchState(
        disconnected(),
        stopController(publishEvents(state, s"Connection for provider [$vendor:$provider] successfully closed."))
          .copy(
            status = DISCONNECTED, lastDisconnect = Some(new Date())
          )
      )

    // Once we encounter a timeout for a connection close we initiate a Container Restart via the monitor
    case CloseTimeout =>
      val e = new Exception(s"Unable to close connection for provider [$vendor:$provider] in [${config.minReconnect}]s]. Restarting container ...")
      monitor ! RestartContainer(e)
  }

  def jmxOperations(state : ConnectionState) : Receive = {
    case cmd : ConnectionCommand =>
      if (cmd.vendor == vendor && cmd.provider == provider) {
        if (cmd.disconnectPending)
          disconnect(state)
        else if (cmd.connectPending)
          self ! CheckConnection(cmd.reconnectNow)
      }
  }

  def handleReconnectRequest(state : ConnectionState) : Receive = {
    case Reconnect(v, p, None) => if (v == vendor && p == provider && state.status == CONNECTED) {
      log.info(s"Initiating reconnect for [$vendor:$provider]")
      reconnect(state)
    }
    case Reconnect(v, p, Some(r)) => if (v == vendor && p == provider && state.status == CONNECTED) {
      log.info(s"Initiating reconnect for [$vendor:$provider] after connection exception [${r.getMessage()}]")
      reconnect(state)
    }
  }

  // helper methods

  // A convenience method to let us know which state we are switching to
  private[this] def switchState(rec: StateReceive, newState: ConnectionState) : Unit = {

    val nextState = publishEvents(newState, s"Connection State Manager [$vendor:$provider] switching to state [${newState.status}]")
    currentReceive = rec
    currentState = nextState
    monitor ! ConnectionStateChanged(nextState)
    context.system.eventStream.publish(ConnectionStateChanged(newState))
    context.become(
      rec(nextState)
        .orElse(jmxOperations(nextState))
        .orElse(handleReconnectRequest(nextState))
        .orElse(controllerStopped(nextState))
        .orElse(unhandled)
    )
  }

  // A convenience method to capture unhandled messages
  private def unhandled : Receive = {
    case m => log.debug(s"received unhandled message for [$vendor:$provider] : ${m.toString()}")
  }

  private def controllerStopped(s: ConnectionState) : Receive = {
    case Terminated(a) => s.controller match {
      case Some(c) if a == c =>
        log.warn(s"The current connection controller das stopped unexpectedly, initiating reconnect")
        reconnect(restartController(s))
    }
  }

  // We simply stay in the same state and maintain the list of events
  def publishEvents(s : ConnectionState, msg: String*) : ConnectionState = {

    msg.foreach(m => log.info(m))
    val tsMsg = msg.map { m => df.format(new Date()) + " " + m }

    val newEvents = if (tsMsg.size >= s.maxEvents) {
      tsMsg.reverse.take(s.maxEvents)
    } else {
      tsMsg.reverse ++ s.events.take(s.maxEvents - tsMsg.size)
    }

    s.copy(events = newEvents.toList)
  }

  // To initialise the connection we check whether we have been connected at some point in the history of
  // this container has been started. If so, we will let not attempt to reconnect before the time specified
  // in the config has passed.
  // If not, we will try to connect immediately.

  private[this] def initConnection(s: ConnectionState, now : Boolean) : Unit = {

    val remaining : Double = s.lastDisconnect match {
      case None => 0
      case Some(l) => config.minReconnect.toMillis - (System.currentTimeMillis() - l.getTime())
    }

    // if we were ever disconnected from the JMS provider since the container start we will check
    // whether the reconnect interval has passed, otherwise we will connect immediately
    if (!now && s.lastDisconnect.isDefined && remaining > 0) {
      switchState(
        currentReceive,
        publishEvents(s, s"Container is waiting to reconnect for provider [$vendor:$provider], remaining wait time [${remaining / 1000.0}]s")
      )
      checkConnection((remaining + 1).seconds)
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

    context.system.scheduler.scheduleOnce(config.connectTimeout, self, ConnectTimeout(lastConnectAttempt))

    // This only happens if we have configured a maximum reconnect timeout in the config and we ever
    // had a connection since this container was last restarted and we haven't started the timer yet
    val newState : ConnectionState = (if (config.maxReconnectTimeout.isDefined && state.firstReconnectAttempt.isEmpty) {
      events = (s"Starting max reconnect timeout monitor for provider [$vendor:$provider] with [${config.maxReconnectTimeout}]s") :: events
      restartController(state).copy(firstReconnectAttempt = Some(lastConnectAttempt))
    } else {
      restartController(state)
    })

    newState.controller.foreach(_ ! Connect(lastConnectAttempt, config.clientId))

    // push the events into the newState in reverse order and set
    // the new state name
    publishEvents(newState, events.reverse.toArray:_*).copy(
      status = CONNECTING,
      lastConnectAttempt = Some(lastConnectAttempt)
    )
  }

  private[this] def restartController(s : ConnectionState) : ConnectionState = {
    val newController : ActorRef = context.actorOf(JmsConnectionController.props(holder, ConnectionCloseActor.props(holder)))
    // We start watching the controller, so we can react in case it dies
    context.watch(newController)

    stopController(s).copy(controller = Some(newController))
  }

  private[this] def stopController(s: ConnectionState) : ConnectionState = {
    s.controller.foreach{ a =>
      log.debug(s"Stopping JMS Connection controller")
      context.unwatch(a)
      context.stop(a)
    }
    s.copy(controller = None)
  }

  private[this] def checkRestartForFailedReconnect(s: ConnectionState, e: Option[Throwable]): Boolean = {

    var result = false

    e.foreach{ t =>
      log.error(t)(s"Error connecting to JMS provider [$vendor:$provider].")
    }

    if (config.maxReconnectTimeout.isDefined && s.firstReconnectAttempt.isDefined) {
      s.firstReconnectAttempt.foreach { t =>

        val restart : Boolean = config.maxReconnectTimeout.exists{ to =>
          (System.currentTimeMillis() - t.getTime()).millis > to
        }

        if (restart) {
          val msg : String = s"Unable to reconnect to JMS provider [$vendor:$provider] in [${config.maxReconnectTimeout}]s. Restarting container ..."
          log.warn(msg)
          monitor ! RestartContainer(new Exception(msg))
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
    s.controller.foreach(_ ! Disconnect(config.minReconnect))
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
    log.info(s"Restarting connection for [$vendor:$provider] in [${config.minReconnect}]")
    checkConnection(config.minReconnect + 1.seconds)
  }

  private[this] def ping(c: Connection) : Unit = {

    if (config.pingEnabled) {
      pinger match {
        case None =>
          log.info(s"Checking JMS connection for provider [$vendor:$provider]")

          pinger = Some(context.actorOf(JmsPingPerformer.props(config, c, new DefaultPingOperations())))
          pinger.foreach(_ ! ExecutePing(self, pingCounter.getAndIncrement()))
        case Some(a) =>
          log.debug(s"Ignoring ping request for provider [$provider] as one pinger is already active.")
      }
    } else {
      log.debug(s"Ping is disabled for connection factory [${config.vendor}:${config.provider}]")
    }
  }
}
