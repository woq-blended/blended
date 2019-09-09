package blended.jms.utils.internal

import akka.actor.{ActorSystem, Props}
import akka.testkit.{ImplicitSender, TestActorRef, TestKit, TestProbe}
import blended.jms.utils._
import blended.testsupport.scalatest.LoggingFreeSpecLike
import blended.util.logging.Logger
import javax.jms.{Connection, ConnectionFactory}

import scala.concurrent.duration._

class ConnectionStateManagerSpec extends TestKit(ActorSystem("ConnectionManger"))
  with LoggingFreeSpecLike
  with ImplicitSender {

  private val log : Logger = Logger[ConnectionStateManagerSpec]

  private val vendor : String = "csm"
  private val provider : String = "csm"

  private val cfg : BlendedJMSConnectionConfig = BlendedJMSConnectionConfig.defaultConfig.copy(
    vendor = vendor,
    provider = provider
  )

  private def connHolder : Int => ConnectionHolder = maxConnects => new DummyHolder(() => new DummyConnection(), maxConnects = maxConnects) {
    override val vendor : String = cfg.vendor
    override val provider : String = cfg.provider
  }

  private def fishForState(probe : TestProbe, duration: FiniteDuration = 3.seconds)(state : JmsConnectionState): ConnectionStateChanged = {
    val msg : Any = probe.fishForMessage(duration){
      case changed : ConnectionStateChanged =>
        log.info(changed.toString())
        changed.state.status.toString == state.toString
    }
    msg.asInstanceOf[ConnectionStateChanged]
  }

  "The Connection State Manager" - {

    "should start in disconnected state" in {

      val holder = connHolder(Int.MaxValue)

      val probe : TestProbe = TestProbe()
      system.eventStream.subscribe(probe.ref, classOf[ConnectionStateChanged])
      val props : Props = ConnectionStateManager.props(cfg, holder)

      val csm = TestActorRef[ConnectionStateManager](props)

      assert(csm.underlyingActor.currentState.status == Disconnected)
      assert(holder.getConnection().isEmpty)

      fishForState(probe)(Disconnected)
    }

    "should switch to connected state upon successful connect" in {
      val holder = connHolder(Int.MaxValue)
      val probe : TestProbe = TestProbe()
      system.eventStream.subscribe(probe.ref, classOf[ConnectionStateChanged])

      val props : Props = ConnectionStateManager.props(cfg, holder)

      val csm = TestActorRef[ConnectionStateManager](props)

      csm ! CheckConnection(false)

      fishForState(probe)(Connected)
      assert(holder.getConnection().isDefined)
    }

    "should switch to disconnected state upon successful disconnect" in {
      val holder = connHolder(Int.MaxValue)
      val probe : TestProbe = TestProbe()
      system.eventStream.subscribe(probe.ref, classOf[ConnectionStateChanged])

      val props : Props = ConnectionStateManager.props(cfg, holder)
      val csm = TestActorRef[ConnectionStateManager](props)

      csm ! CheckConnection(false)
      fishForState(probe)(Connected)

      csm ! Disconnect(1.second)
      fishForState(probe)(Disconnected)

      assert(holder.getConnection().isEmpty)
    }

    "should disconnect upon a MaxKeepAliveExceeded event" in {
      val holder = connHolder(Int.MaxValue)

      val probe : TestProbe = TestProbe()
      system.eventStream.subscribe(probe.ref, classOf[ConnectionStateChanged])
      val props : Props = ConnectionStateManager.props(
        cfg.copy(
          minReconnect = 3.seconds
        ),
        holder
      )

      val csm = TestActorRef[ConnectionStateManager](props)

      csm ! CheckConnection(false)
      fishForState(probe)(Connected)

      csm ! MaxKeepAliveExceeded(vendor, provider)
      fishForState(probe)(Disconnected)

      assert(csm.underlyingActor.currentState.lastDisconnect.isDefined)
      assert(holder.getConnection().isEmpty)
      fishForState(probe, 5.seconds)(Connected)

      assert(holder.getConnection().isDefined)
    }

    "should issue another connect in case the first connect times out" in {

      val delayedFirstConnect : ConnectionHolder = new DummyHolder(() => new DummyConnection()) {
        private var firstTry : Boolean = true
        override val vendor: String = cfg.vendor
        override val provider: String = cfg.provider

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

      val props = ConnectionStateManager.props(
        cfg.copy(
          connectTimeout = 1.second,
          minReconnect = 3.seconds
        ),
        delayedFirstConnect
      )

      val csm = TestActorRef[ConnectionStateManager](props)

      csm ! CheckConnection(false)

      fishForState(probe)(Connecting)
      fishForState(probe)(Connecting)
      fishForState(probe, 10.seconds)(Connected)
    }

    "should initiate a container restart if the initial JMS connection can't be established" in {
      val neverConnect : ConnectionHolder = new DummyHolder(() => new DummyConnection()) {
        override val vendor: String = cfg.vendor
        override val provider: String = cfg.provider

        override def connect(): Connection = {
          // scalastyle:off magic.number
          Thread.sleep(60000)
          // scalastyle:on magic.number
          super.connect()
        }
      }

      val probe = TestProbe()
      system.eventStream.subscribe(probe.ref, classOf[ConnectionStateChanged])

      val props = ConnectionStateManager.props(
        cfg.copy(
          connectTimeout = 1.second,
          minReconnect = 1.seconds,
          maxReconnectTimeout = Some(5.seconds)
        ),
        neverConnect
      )

      val csm = TestActorRef[ConnectionStateManager](props)

      csm ! CheckConnection(false)

      probe.fishForMessage(10.seconds) {
        case s : ConnectionStateChanged if s.state.status.isInstanceOf[RestartContainer] => true
        case _ => false
      }
    }

    "should successfully connect even after the connection holder threw unexpected exceptions" in {
      val failFirst : ConnectionHolder = new DummyHolder(() => new DummyConnection()) {
        private var first : Boolean = true
        override val vendor: String = cfg.vendor
        override val provider: String = cfg.provider

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

      val props = ConnectionStateManager.props(
        cfg.copy(
          connectTimeout = 1.second,
          minReconnect = 2.seconds,
          maxReconnectTimeout = Some(10.seconds)
        ),
        failFirst
      )

      val csm = TestActorRef[ConnectionStateManager](props)

      csm ! CheckConnection(false)

      fishForState(probe)(Connecting)
      fishForState(probe, 10.seconds)(Connected)
    }

    "should disconnect if the current connection controller dies" in {

      val holder : ConnectionHolder = connHolder(Int.MaxValue)
      val props = ConnectionStateManager.props(cfg.copy(
        connectTimeout = 1.seconds,
        minReconnect = 2.seconds
      ), holder)

      val probe = TestProbe()
      system.eventStream.subscribe(probe.ref, classOf[ConnectionStateChanged])

      val csm = TestActorRef[ConnectionStateManager](props)

      csm ! CheckConnection(false)

      fishForState(probe)(Connected)
      assert(holder.getConnection().isDefined)

      csm.underlyingActor.currentState.controller.foreach(system.stop)

      fishForState(probe)(Disconnected)
      fishForState(probe)(Connected)
    }

    "should initiate a container restart if the connection can't be established from connecting state" in {
      val onlyConnectOnce : ConnectionHolder = new DummyHolder(() => new DummyConnection()) {
        private var first : Boolean = true
        override val vendor: String = cfg.vendor
        override val provider: String = cfg.provider

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

      val props = ConnectionStateManager.props(
        cfg.copy(
          connectTimeout = 1.second,
          minReconnect = 2.seconds,
          maxReconnectTimeout = Some(10.seconds)
        ),
        onlyConnectOnce
      )

      val csm = TestActorRef[ConnectionStateManager](props)

      csm ! CheckConnection(false)

      fishForState(probe)(Connecting)
      fishForState(probe)(Connected)

      csm ! Disconnect(1.second)

      fishForState(probe)(Disconnected)

      probe.fishForMessage(20.seconds) {
        case sc : ConnectionStateChanged => sc.state.status.isInstanceOf[RestartContainer]
        case _ => false
      }
    }

    "should initiate a container restart if it can't reconnect within the maxReconnectInterval" in {
      // Allow max 1 connection for the dummy holder, so we never can reconnect
      val holder = connHolder(1)

      val probe : TestProbe = TestProbe()
      system.eventStream.subscribe(probe.ref, classOf[ConnectionStateChanged])

      val props : Props = ConnectionStateManager.props(
        cfg.copy(
          minReconnect = 1.seconds,
          maxReconnectTimeout = Some(3.seconds),
          retryInterval = 1.second
        ),
        holder
      )

      val csm = TestActorRef[ConnectionStateManager](props)

      csm ! CheckConnection(false)
      fishForState(probe)(Connected)

      csm ! MaxKeepAliveExceeded(vendor, provider)
      fishForState(probe)(Disconnected)

      assert(csm.underlyingActor.currentState.lastDisconnect.isDefined)
      assert(holder.getConnection().isEmpty)
      probe.fishForMessage(5.seconds){
        case m : ConnectionStateChanged if m.state.status.isInstanceOf[RestartContainer] => true
        case _ => false
      }

      assert(holder.getConnection().isEmpty)
    }
  }
}
