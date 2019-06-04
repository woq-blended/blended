package blended.jms.utils.internal

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestActorRef, TestKit, TestProbe}
import blended.jms.utils.BlendedJMSConnectionConfig
import org.scalatest.FreeSpecLike
import scala.concurrent.duration._

class ConnectionStateManagerSpec extends TestKit(ActorSystem("ConnectionManger"))
  with FreeSpecLike
  with ImplicitSender {

  val cfg = BlendedJMSConnectionConfig.defaultConfig.copy(
    vendor = "csm",
    provider = "csm"
  )

  val holder = new DummyHolder(() => new DummyConnection()) {
    override val vendor : String = cfg.vendor
    override val provider : String = cfg.provider
  }

  "The Connection State Manager" - {

    "should start in disconnected state" in {

      val probe = TestProbe()
      val props = ConnectionStateManager.props(cfg, probe.ref, holder)

      val csm = TestActorRef[ConnectionStateManager](props)

      assert(csm.underlyingActor.currentState.status == ConnectionState.DISCONNECTED)
      assert(holder.getConnection().isEmpty)
    }

    "should switch to connected state upon successful connect" in {
      val probe = TestProbe()
      val props = ConnectionStateManager.props(cfg, probe.ref, holder)

      val csm = TestActorRef[ConnectionStateManager](props)

      csm ! CheckConnection(false)
      probe.fishForMessage(3.seconds) {
        case sc : ConnectionStateChanged => sc.state.status == ConnectionState.CONNECTED
        case _                           => false
      }
      assert(holder.getConnection().isDefined)
    }

    "should switch to disconnected state upon successful disconnect" in {
      val probe = TestProbe()
      val props = ConnectionStateManager.props(cfg, probe.ref, holder)

      val csm = TestActorRef[ConnectionStateManager](props)

      csm ! CheckConnection(false)
      probe.fishForMessage(3.seconds) {
        case sc : ConnectionStateChanged => sc.state.status == ConnectionState.CONNECTED
        case _                           => false
      }

      csm ! Disconnect(1.second)
      probe.fishForMessage(3.seconds) {
        case sc : ConnectionStateChanged => sc.state.status == ConnectionState.DISCONNECTED
        case _                           => false
      }
      assert(holder.getConnection().isEmpty)
    }

    "should disconnect after the specified number of failed pings and reconnect after the minReconnect delay" in {
      val probe = TestProbe()
      val props = ConnectionStateManager.props(
        cfg.copy(
          minReconnect = 3.seconds
        ),
        probe.ref,
        holder
      )

      val csm = TestActorRef[ConnectionStateManager](props)

      csm ! CheckConnection(false)
      probe.fishForMessage(3.seconds) {
        case sc : ConnectionStateChanged => sc.state.status == ConnectionState.CONNECTED
        case _                           => false
      }

      (1.to(cfg.pingTolerance)).foreach { _ => csm ! PingFailed(new Exception("Boom")) }
      probe.fishForMessage(3.seconds) {
        case sc : ConnectionStateChanged => sc.state.status == ConnectionState.DISCONNECTED
        case _                           => false
      }
      assert(csm.underlyingActor.currentState.lastDisconnect.isDefined)
      assert(holder.getConnection().isEmpty)

      probe.fishForMessage(5.seconds) {
        case sc : ConnectionStateChanged => sc.state.status == ConnectionState.CONNECTED
        case _                           => false
      }
      assert(holder.getConnection().isDefined)
    }

  }
}
