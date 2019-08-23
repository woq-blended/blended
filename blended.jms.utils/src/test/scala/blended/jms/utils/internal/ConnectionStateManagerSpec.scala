package blended.jms.utils.internal

import akka.actor.{ActorSystem, Props}
import akka.testkit.{ImplicitSender, TestActorRef, TestKit, TestProbe}
import blended.jms.utils._
import blended.testsupport.scalatest.LoggingFreeSpecLike
import blended.util.logging.Logger
import javax.jms.Connection

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

      probe.fishForMessage(3.seconds) {
        case sc : ConnectionStateChanged => sc.state.status == Connecting
        case _ => false
      }

      probe.fishForMessage(3.seconds) {
        case sc : ConnectionStateChanged => sc.state.status == Connecting
        case _ => false
      }

      probe.fishForMessage(10.seconds) {
        case sc : ConnectionStateChanged => sc.state.status == Connected
        case _ => false
      }
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

      probe.fishForMessage(3.seconds) {
        case sc : ConnectionStateChanged => sc.state.status == Connecting
        case _ => false
      }

      probe.fishForMessage(3.seconds) {
        case sc : ConnectionStateChanged => sc.state.status == Connected
        case _ => false
      }

      csm ! Disconnect(1.second)

      probe.fishForMessage(3.seconds) {
        case sc : ConnectionStateChanged => sc.state.status == Disconnected
        case _ => false
      }

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
      fishForState(probe, 5.seconds)(RestartContainer(new Exception("Max connects exceeded")))

      assert(holder.getConnection().isEmpty)
    }
  }
}
