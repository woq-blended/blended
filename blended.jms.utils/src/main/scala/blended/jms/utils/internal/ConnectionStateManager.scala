package blended.jms.utils.internal

import java.text.SimpleDateFormat
import java.util.Date

import akka.actor.{Actor, ActorRef, Props, Terminated}
import blended.jms.utils._
import blended.util.logging.Logger

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

object ConnectionStateManager {

  def props(
    config : ConnectionConfig,
    holder : ConnectionHolder
  ) : Props =
    Props(new ConnectionStateManager(config, holder))
}

class ConnectionStateManager(config: ConnectionConfig, holder: ConnectionHolder)
  extends Actor {

  type StateReceive = ConnectionState => Receive

  private val df = new SimpleDateFormat("yyyyMMdd-HHmmss-SSS")

  implicit val eCtxt : ExecutionContext = context.system.dispatcher
  private val log : Logger = Logger(s"${getClass().getName()}.${config.vendor}.${config.provider}")

  private var conn : Option[BlendedJMSConnection] = None

  private var currentReceive : StateReceive = disconnected()
  private[internal] var currentState : ConnectionState = ConnectionState(
    vendor = config.vendor,
    provider = config.provider
  )


  // To this actor we delegate all connect and close operations for the underlying JMS provider
  val controller : ActorRef = context.actorOf(JmsConnectionController.props(holder, ConnectionCloseActor.props(holder)))

  // If something causes an unexpected restart, we want to know
  override def preRestart(reason : Throwable, message : Option[Any]) : Unit = {
    log.error(s"Error encountered in Connection State Manager : [${reason.getMessage}], restarting ...")
    super.preRestart(reason, message)
  }

  // We clean up our JMS connections
  override def postStop() : Unit = {
    log.info(s"Stopping Connection State Manager")
    disconnect(currentState)
    super.postStop()
  }

  // The initial state is disconnected
  override def receive : Actor.Receive = Actor.emptyBehavior

  override def preStart() : Unit = {
    super.preStart()
    switchState(disconnected(), currentState)
    context.system.eventStream.subscribe(self, classOf[ConnectionCommand])
    context.system.eventStream.subscribe(self, classOf[Reconnect])
    context.system.eventStream.subscribe(self, classOf[KeepAliveEvent])
  }

  // ---- State: Disconnected
  def disconnected()(state: ConnectionState) : Receive = {

    // Upon a CheckConnection message we will kick off initiating and monitoring the connection
    case cc : CheckConnection =>
      log.debug(s"Trying to initialize connection")
      initConnection(state, cc.now)

    case _ : KeepAliveEvent => // do nothing
  }

  // ---- State: Connected
  def connected()(state : ConnectionState) : Receive = {

    // we simply eat up the CloseTimeOut messages that might still be going for previous
    // connect attempts
    case ConnectTimeout(_) => // do nothing, this will just get rid of irrelevant warnings in the log

    case cc : CheckConnection => // do nothing

    case d @ Disconnect(_) => disconnect(state)

    case KeepAliveMissed(v,p,n) =>
      if(config.vendor == v && config.provider == p) {
        log.debug(s"Updating missed KeepAlives to [$n]")
        switchState(connected(), state.copy(missedKeepAlives = n))
      }

    case MaxKeepAliveExceeded(v, p) =>
      if (config.vendor == v && config.provider == p) {
        log.warn(s"Maximum number of missed keep alives has been reached : [${config.maxKeepAliveMissed}]")
        reconnect(state)
      }

    case _ : KeepAliveEvent => // do nothing
  }

  // ---- State: Connecting
  def connecting()(state : ConnectionState) : Receive = {

    case _ : CheckConnection =>

    case ConnectResult(t, Left(e)) =>
      if (t == state.lastConnectAttempt.getOrElse(new Date())) {
        state.controller.foreach(context.system.stop)
        switchState(disconnected(), stopController(state).copy(status = Disconnected))
        if (!checkRestartForFailedReconnect(state, Some(e))) {
          checkConnection(config.minReconnect)
        }
      }

    // We successfully connected, record the connection and timestamps
    case ConnectResult(t, Right(c)) =>
      if (t == state.lastConnectAttempt.getOrElse(0L)) {
        conn = Some(new BlendedJMSConnection(c))
        // checkConnection(schedule)
        switchState(connected(), publishEvents(state, s"Successfully connected to JMS provider").copy(
          status = Connected,
          firstReconnectAttempt = None,
          lastConnect = Some(new Date()),
          missedKeepAlives = 0
        ))
      }

    case ConnectTimeout(t) =>
      log.debug(s"Encountered Connection timeout (connectAttempt:$t), firstConnectAttempt: ${state.firstReconnectAttempt}, " +
        s"lastConnectAttempt: ${state.lastConnectAttempt}")
      if (!checkRestartForFailedReconnect(state, None)) {
        switchState(connecting(), connect(state))
      }

    case _ : KeepAliveEvent => // do nothing
  }

  // State: Closing
  def closing()(state : ConnectionState) : Receive = {

    case _ : CheckConnection => // do nothing

    // All good, happily disconnected
    case ConnectionClosed =>
      conn = None
      checkConnection(config.minReconnect, true)
      state.controller.foreach(context.system.stop)
      switchState(
        disconnected(),
        stopController(publishEvents(state, s"JMS connection successfully closed."))
          .copy(
            status = Disconnected, lastDisconnect = Some(new Date())
          )
      )

    // Once we encounter a timeout for a connection close we initiate a Container Restart via the monitor
    case CloseTimeout =>
      val msg : String = s"Unable to close JMS connection for provider in [${config.minReconnect}]s]. Restarting container ..."
      switchState(restarting(), state.copy(status = RestartContainer(new Exception(msg))))

    case _ : KeepAliveEvent => // do nothing
  }

  // Container restart is pending
  def restarting()(state : ConnectionState) : Receive = Actor.emptyBehavior

  def jmxOperations(state : ConnectionState) : Receive = {
    case cmd : ConnectionCommand =>
      if (cmd.vendor == config.vendor && cmd.provider == config.provider) {
        if (cmd.disconnectPending) {
          disconnect(state)
        } else {
          if (cmd.connectPending) {
            self ! CheckConnection(cmd.reconnectNow)
          }
        }
      }
  }

  def handleReconnectRequest(state : ConnectionState) : Receive = {
    case Reconnect(v, p, None) => if (v == config.vendor && p == config.provider) {
      log.info(s"Initiating JMS reconnect for")
      reconnect(state)
    }
    case Reconnect(v, p, Some(r)) => if (v == config.vendor && p == config.provider) {
      log.info(s"Initiating JMS reconnect after connection exception [${r.getMessage()}]")
      reconnect(state)
    }
  }

  // helper methods

  // A convenience method to let us know which state we are switching to
  private[this] def switchState(rec : StateReceive, newState : ConnectionState) : Unit = {

    val nextState : ConnectionState =
      publishEvents(newState, s"Connection State Manager switching to state [${newState.status}]")

    currentReceive = rec
    currentState = nextState
    context.system.eventStream.publish(ConnectionStateChanged(nextState))
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
    case m => log.debug(s"received unhandled message : ${m.toString()}")
  }

  private def controllerStopped(s: ConnectionState) : Receive = {
    case Terminated(a) => s.controller match {
      case Some(c) if a == c =>
        log.warn(s"The current connection controller das stopped unexpectedly, initiating reconnect")
        reconnect(restartController(s))
    }
  }

  // We simply stay in the same state and maintain the list of events
  def publishEvents(s : ConnectionState, msg : String*) : ConnectionState = {

    msg.foreach(m => log.info(m))
    val tsMsg = msg.map { m => df.format(new Date()) + " " + m }

    val newEvents = if (tsMsg.size >= s.maxEvents) {
      tsMsg.reverse.take(s.maxEvents)
    } else {
      tsMsg.reverse ++ s.events.take(s.maxEvents - tsMsg.size)
    }

    val newState : ConnectionState = s.copy(events = newEvents.toList)
    newState
  }

  // To initialise the connection we check whether we have been connected at some point in the history of
  // this container has been started. If so, we will let not attempt to reconnect before the time specified
  // in the config has passed.
  // If not, we will try to connect immediately.

  private[this] def initConnection(s : ConnectionState, now : Boolean) : Unit = {

    val remaining : Double = s.lastDisconnect match {
      case None    => 0
      case Some(l) => config.minReconnect.toMillis - (System.currentTimeMillis() - l.getTime())
    }

    // if we were ever disconnected from the JMS provider since the container start we will check
    // whether the reconnect interval has passed, otherwise we will connect immediately
    if (!now && s.lastDisconnect.isDefined && remaining > 0) {
      switchState(
        currentReceive,
        publishEvents(s, s"Container is waiting to reconnect, remaining wait time [${remaining / 1000.0}]s")
      )
      checkConnection((remaining + 1).seconds)
    } else {
      switchState(connecting(), connect(s))
    }
  }

  // A simple convenience method to schedule the next connection check to ourselves
  private[this] def checkConnection(delay : FiniteDuration, force : Boolean = false) : Unit = {
    log.info(s"Scheduling JMS connection check in [$delay]")
    context.system.scheduler.scheduleOnce(delay, self, CheckConnection(false))
  }

  private[this] def connect(state : ConnectionState) : ConnectionState = {

    var events : List[String] = List(s"Creating connection to JMS")

    val lastConnectAttempt = new Date()

    context.system.scheduler.scheduleOnce(config.connectTimeout, self, ConnectTimeout(lastConnectAttempt))

    // This only happens if we have configured a maximum reconnect timeout in the config and we ever
    // had a connection since this container was last restarted and we haven't started the timer yet
    val newState : ConnectionState = (if (config.maxReconnectTimeout.isDefined && state.firstReconnectAttempt.isEmpty) {
      events = (s"Starting max connect timeout monitor with [${config.maxReconnectTimeout}]s") :: events
      restartController(state).copy(firstReconnectAttempt = Some(lastConnectAttempt))
    } else {
      restartController(state)
    })

    newState.controller.foreach(_ ! Connect(lastConnectAttempt, config.clientId))

    // push the events into the newState in reverse order and set
    // the new state name
    publishEvents(newState, events.reverse.toArray : _*).copy(
      status = Connecting,
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
      log.error(t)(s"Error connecting to JMS provider.")
    }

    if (config.maxReconnectTimeout.isDefined && s.firstReconnectAttempt.isDefined) {
      s.firstReconnectAttempt.foreach { t =>

        val restart : Boolean = config.maxReconnectTimeout.exists { to =>
          (System.currentTimeMillis() - t.getTime()).millis > to
        }

        if (restart) {
          val msg : String = s"Unable to reconnect to JMS provider in [${config.maxReconnectTimeout}]s. Restarting container ..."
          log.warn(msg)
          context.system.eventStream.publish(ConnectionStateChanged(s.copy(status = RestartContainer(new Exception(msg)))))
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
    // Notify the connection controller of the disconnect
    s.controller.foreach(_ ! Disconnect(config.minReconnect))
    switchState(closing(), s.copy(status = Closing))
  }

  private[this] def reconnect(s : ConnectionState) : Unit = {
    disconnect(s)
    log.info(s"Restarting JMS connection in [${config.minReconnect}]")
    checkConnection(config.minReconnect)
  }
}
