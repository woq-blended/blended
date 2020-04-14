package blended.jms.utils.internal

import akka.actor.{ActorSystem, Props}
import akka.testkit.{ImplicitSender, TestActorRef, TestKit, TestProbe}
import blended.jms.utils._
import blended.testsupport.scalatest.LoggingFreeSpecLike
import blended.util.logging.Logger
import javax.jms.{Connection, ConnectionFactory}
import BlendedJMSConnectionConfig.defaultConfig
import scala.concurrent.duration._

class ConnectionStateManagerSpec extends TestKit(ActorSystem("ConnectionManger"))
  with LoggingFreeSpecLike
  with ImplicitSender {

  private val log : Logger = Logger[ConnectionStateManagerSpec]

  private def connHolder(
    cfg: ConnectionConfig,
    maxConnects : Int = Int.MaxValue
  ) : ConnectionHolder =  new DummyHolder(cfg, maxConnects = maxConnects) {

    override val config: ConnectionConfig = cfg
  }

  private def fishForState(probe : TestProbe, cfg: ConnectionConfig, timeout: FiniteDuration = 3.seconds)(state : JmsConnectionState): ConnectionStateChanged = {
    log.info(s"Waiting for state [${cfg.vendor}:${cfg.provider}] : [$state] - [$timeout]")
    val msg : Any = probe.fishForMessage(timeout){
      case changed : ConnectionStateChanged =>
        log.info(changed.toString())
        changed.state.vendor == cfg.vendor &&
        changed.state.provider == cfg.provider &&
        changed.state.status.toString == state.toString
    }
    msg.asInstanceOf[ConnectionStateChanged]
  }

  "The Connection State Manager" - {

    "should start in disconnected state" in {

      val holder = connHolder(defaultConfig.copy(
        vendor = "csm", provider = "disconnected", clientId = "disconnected",
      ))

      val probe : TestProbe = TestProbe()
      system.eventStream.subscribe(probe.ref, classOf[ConnectionStateChanged])
      val props : Props = ConnectionStateManager.props(holder)

      val csm = TestActorRef[ConnectionStateManager](props)

      assert(csm.underlyingActor.currentState.status == Disconnected)
      assert(holder.getConnection().isEmpty)

      fishForState(probe, holder.config)(Disconnected)
      system.stop(csm)
      system.stop(probe.ref)
    }

    "should switch to connected state upon successful connect" in {
      val holder = connHolder(defaultConfig.copy(
        vendor = "csm", provider = "connect", clientId = "connect",
      ))

      val probe : TestProbe = TestProbe()
      system.eventStream.subscribe(probe.ref, classOf[ConnectionStateChanged])

      val props : Props = ConnectionStateManager.props(holder)

      val csm = TestActorRef[ConnectionStateManager](props)

      csm ! CheckConnection(false)

      fishForState(probe, holder.config)(Connected)
      assert(holder.getConnection().isDefined)

      system.stop(csm)
      system.stop(probe.ref)
    }

    "should switch to disconnected state upon successful disconnect" in {
      val holder = connHolder(defaultConfig.copy(
        vendor = "csm", provider = "disconnect", clientId = "disconnect",
      ))

      val probe : TestProbe = TestProbe()
      system.eventStream.subscribe(probe.ref, classOf[ConnectionStateChanged])

      val props : Props = ConnectionStateManager.props(holder)
      val csm = TestActorRef[ConnectionStateManager](props)

      csm ! CheckConnection(false)
      fishForState(probe, holder.config)(Connected)

      csm ! Disconnect(1.second)
      fishForState(probe, holder.config)(Disconnected)

      assert(holder.getConnection().isEmpty)

      system.stop(csm)
      system.stop(probe.ref)
    }

    "should disconnect upon a MaxKeepAliveExceeded event" in {
      val holder = connHolder(defaultConfig.copy(
        vendor = "csm", provider = "disconnectMaxKA", clientId = "disconnectMaxKA",
        minReconnect = 3.seconds
      ))

      val probe : TestProbe = TestProbe()
      system.eventStream.subscribe(probe.ref, classOf[ConnectionStateChanged])
      val props : Props = ConnectionStateManager.props(holder)

      val csm = TestActorRef[ConnectionStateManager](props)

      csm ! CheckConnection(false)
      fishForState(probe, holder.config)(Connected)

      csm ! MaxKeepAliveExceeded(holder.config.vendor, holder.config.provider)
      fishForState(probe, holder.config)(Disconnected)

      assert(csm.underlyingActor.currentState.lastDisconnect.isDefined)
      assert(holder.getConnection().isEmpty)
      fishForState(probe, holder.config, 5.seconds)(Connected)

      assert(holder.getConnection().isDefined)

      system.stop(csm)
      system.stop(probe.ref)
    }

    "should issue another connect in case the first connect times out" in {

      val delayedFirstConnect : ConnectionHolder = new DummyHolder(
        defaultConfig.copy(
          vendor = "csm", provider = "firstConnectTO", clientId = "firstConnectTO",
          connectTimeout = 1.second,
          minReconnect = 2.seconds
        ), c => new DummyConnection(c) {
        }
      ) {
        private var firstTry : Boolean = true

        override def connect(): Connection = {
          if (firstTry) {
            Thread.sleep(5000)
            firstTry = false
          }
          super.connect()
        }
      }

      val probe = TestProbe()
      system.eventStream.subscribe(probe.ref, classOf[ConnectionStateChanged])

      val props = ConnectionStateManager.props(delayedFirstConnect)

      val csm = TestActorRef[ConnectionStateManager](props)

      csm ! CheckConnection(false)

      fishForState(probe, cfg = delayedFirstConnect.config)(Connecting)
      fishForState(probe, cfg = delayedFirstConnect.config)(Connecting)
      fishForState(probe, cfg = delayedFirstConnect.config, timeout = 10.seconds)(Connected)

      system.stop(csm)
      system.stop(probe.ref)
    }

    "should initiate a container restart if the initial JMS connection can't be established" in {
      val neverConnect : ConnectionHolder = new DummyHolder(
        defaultConfig.copy(
          vendor = "csm", provider = "noInitial", clientId = "noInitial",
          connectTimeout = 1.second,
          minReconnect = 2.seconds,
          maxReconnectTimeout = Some(5.seconds)
        )
      ) {
        override def connect(): Connection = {
          // scalastyle:off magic.number
          log.info(s"Sleeping 60 seconds before connect")
          Thread.sleep(60000)
          // scalastyle:on magic.number
          super.connect()
        }
      }

      val probe = TestProbe()
      system.eventStream.subscribe(probe.ref, classOf[ConnectionStateChanged])

      val props = ConnectionStateManager.props(
        neverConnect
      )

      val csm = TestActorRef[ConnectionStateManager](props)

      csm ! CheckConnection(false)

      probe.fishForMessage(10.seconds) {
        case s : ConnectionStateChanged if s.state.status.isInstanceOf[RestartContainer] => true
        case _ => false
      }

      system.stop(csm)
      system.stop(probe.ref)
    }

    "should successfully connect even after the connection holder threw unexpected exceptions" in {
      val failFirst : ConnectionHolder = new DummyHolder(defaultConfig.copy(
        vendor = "csm", provider = "unexpected", clientId = "unexpected",
        connectTimeout = 1.second,
        minReconnect = 2.seconds,
        maxReconnectTimeout = Some(10.seconds)
      )) {
        private var first : Boolean = true

        override def getConnectionFactory(): ConnectionFactory = {
          if (first) {
            first = false
            throw new NullPointerException()
          } else {
            super.getConnectionFactory()
          }
        }
      }

      val probe = TestProbe()
      system.eventStream.subscribe(probe.ref, classOf[ConnectionStateChanged])

      val props = ConnectionStateManager.props(failFirst)

      val csm = TestActorRef[ConnectionStateManager](props)

      csm ! CheckConnection(false)

      fishForState(probe, failFirst.config)(Connecting)
      fishForState(probe, cfg = failFirst.config, timeout = 10.seconds)(Connected)

      system.stop(csm)
      system.stop(probe.ref)
    }

    "should disconnect if the current connection controller dies" in {

      val holder : ConnectionHolder = new DummyHolder(defaultConfig.copy(
        vendor = "csm", provider = "ctrlDies", clientId = "ctrlDies",
        connectTimeout = 1.second,
        minReconnect = 2.seconds
      ))

      val props = ConnectionStateManager.props(holder)

      val probe = TestProbe()
      system.eventStream.subscribe(probe.ref, classOf[ConnectionStateChanged])

      val csm = TestActorRef[ConnectionStateManager](props)

      csm ! CheckConnection(false)

      fishForState(probe, holder.config)(Connected)
      assert(holder.getConnection().isDefined)

      csm.underlyingActor.currentState.controller.foreach(system.stop)

      fishForState(probe, holder.config)(Disconnected)
      fishForState(probe, holder.config)(Connected)

      system.stop(csm)
      system.stop(probe.ref)
    }

    "should successfully reconnect if a connection level exception is encountered in disconnected state" in {
      val holder : ConnectionHolder = connHolder(defaultConfig.copy(
        vendor = "csm", provider = "connExDisc", clientId = "connExDisc",
        connectTimeout = 1.seconds,
        minReconnect = 2.seconds
      ))
      val probe = TestProbe()
      system.eventStream.subscribe(probe.ref, classOf[ConnectionStateChanged])
      val props = ConnectionStateManager.props(holder)

      val csm = TestActorRef[ConnectionStateManager](props)

      csm ! CheckConnection(false)
      fishForState(probe, cfg = holder.config, timeout = 3.seconds)(Connected)
      assert(holder.getConnection().isDefined)

      csm ! Disconnect(1.second)

      fishForState(probe, cfg = holder.config, timeout = 3.seconds)(Disconnected)

      csm ! Reconnect(holder.config.vendor, holder.config.provider, Some(new Exception("Boom")))

      fishForState(probe, cfg = holder.config, timeout = 10.seconds)(Connected)

      system.stop(csm)
      system.stop(probe.ref)
    }

    "should successfully reconnect in case a connection level exception is thrown" in {
      val holder : ConnectionHolder = connHolder(defaultConfig.copy(
        vendor = "csm", provider = "connEx", clientId = "connEx",
        connectTimeout = 1.seconds,
        minReconnect = 2.seconds
      ))

      val probe = TestProbe()
      system.eventStream.subscribe(probe.ref, classOf[ConnectionStateChanged])

      val props = ConnectionStateManager.props(holder)

      val csm = TestActorRef[ConnectionStateManager](props)

      csm ! CheckConnection(false)
      fishForState(probe, cfg = holder.config, timeout = 3.seconds)(Connected)
      assert(holder.getConnection().isDefined)

      csm ! Reconnect(holder.config.vendor, holder.config.provider, Some(new Exception("Boom")))

      fishForState(probe, cfg = holder.config, timeout = 3.seconds)(Disconnected)
      fishForState(probe, cfg = holder.config, timeout = 10.seconds)(Connected)

      system.stop(csm)
      system.stop(probe.ref)
    }

    "should initiate a container restart if the connection can't be established from connecting state" in {
      val onlyConnectOnce : ConnectionHolder = new DummyHolder(defaultConfig.copy(
        vendor = "csm", provider = "restart", clientId = "restart",
        connectTimeout = 1.second,
        minReconnect = 2.seconds,
        maxReconnectTimeout = Some(5.seconds)
      )) {
        private var first : Boolean = true

        override def connect(): Connection = {
          if (!first) {
            Thread.sleep(60000)
          }
          first = false
          super.connect()
        }
      }

      val probe = TestProbe()
      system.eventStream.subscribe(probe.ref, classOf[ConnectionStateChanged])

      val props = ConnectionStateManager.props(onlyConnectOnce)

      val csm = TestActorRef[ConnectionStateManager](props)

      csm ! CheckConnection(false)

      fishForState(probe, cfg = onlyConnectOnce.config)(Connecting)
      fishForState(probe, cfg = onlyConnectOnce.config)(Connected)

      csm ! Disconnect(1.second)

      fishForState(probe, cfg = onlyConnectOnce.config)(Disconnected)

      probe.fishForMessage(10.seconds) {
        case sc : ConnectionStateChanged =>
          sc.state.vendor == onlyConnectOnce.vendor &&
          sc.state.provider == onlyConnectOnce.provider &&
          sc.state.status.isInstanceOf[RestartContainer]
        case _ => false
      }

      system.stop(csm)
      system.stop(probe.ref)
    }

    "should initiate a container restart if it can't reconnect within the maxReconnectInterval" in {
      // Allow max 1 connection for the dummy holder, so we never can reconnect
      val holder = connHolder(cfg = defaultConfig.copy(
        vendor = "csm", provider = "restart2", clientId = "restart2",
        minReconnect = 1.seconds,
        maxReconnectTimeout = Some(5.seconds)
      ), maxConnects = 1)

      val probe : TestProbe = TestProbe()
      system.eventStream.subscribe(probe.ref, classOf[ConnectionStateChanged])

      val props : Props = ConnectionStateManager.props(holder)

      val csm = TestActorRef[ConnectionStateManager](props)

      csm ! CheckConnection(false)
      fishForState(probe, holder.config)(Connected)

      csm ! MaxKeepAliveExceeded(holder.vendor, holder.provider)
      fishForState(probe, cfg = holder.config)(Disconnected)

      assert(csm.underlyingActor.currentState.lastDisconnect.isDefined)
      assert(holder.getConnection().isEmpty)
      probe.fishForMessage(10.seconds){
        case m : ConnectionStateChanged =>
          m.state.vendor == holder.vendor &&
          m.state.provider == holder.provider &&
          m.state.status.isInstanceOf[RestartContainer]
      }

      assert(holder.getConnection().isEmpty)

      system.stop(csm)
      system.stop(probe.ref)
    }
  }
}
