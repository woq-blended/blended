package blended.jms.utils.internal

import akka.actor.{ActorSystem, Props}
import akka.testkit.{ImplicitSender, TestActorRef, TestKit, TestProbe}
import blended.jms.utils.ConnectionState._
import blended.jms.utils._
import blended.util.logging.Logger
import org.scalatest.FreeSpecLike

import scala.concurrent.duration._

class ConnectionStateManagerSpec extends TestKit(ActorSystem("ConnectionManger"))
  with FreeSpecLike
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

  private def fishForState(probe : TestProbe, duration: FiniteDuration = 3.seconds)(state : ConnectionState.State) = {
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
