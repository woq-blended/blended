package blended.jms.utils.internal

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestActorRef, TestKit, TestProbe}
import blended.jms.utils.BlendedJMSConnectionConfig
import blended.testsupport.scalatest.LoggingFreeSpecLike
import javax.jms.Connection

import scala.concurrent.duration._

class ConnectionStateManagerSpec extends TestKit(ActorSystem("ConnectionManger"))
  with LoggingFreeSpecLike
  with ImplicitSender {

  val cfg = BlendedJMSConnectionConfig.defaultConfig.copy(
    vendor = "csm",
    provider = "csm"
  )

  private val holder : ConnectionHolder = new DummyHolder(() => new DummyConnection()) {
    override val vendor: String = cfg.vendor
    override val provider: String = cfg.provider
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
        case _ => false
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
        case _ => false
      }

      csm ! Disconnect(1.second)
      probe.fishForMessage(3.seconds) {
        case sc : ConnectionStateChanged => sc.state.status == ConnectionState.DISCONNECTED
        case _ => false
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
        case _ => false
      }

      1.to(cfg.pingTolerance).foreach { _ => csm ! PingFailed(new Exception("Boom")) }
      probe.fishForMessage(3.seconds) {
        case sc : ConnectionStateChanged => sc.state.status == ConnectionState.DISCONNECTED
        case _ => false
      }
      assert(csm.underlyingActor.currentState.lastDisconnect.isDefined)
      assert(holder.getConnection().isEmpty)

      probe.fishForMessage(5.seconds) {
        case sc : ConnectionStateChanged => sc.state.status == ConnectionState.CONNECTED
        case _ => false
      }
      assert(holder.getConnection().isDefined)
    }

    "should issue another connect in case the first connect times out" in {

      val delayedFirstConnect = new DummyHolder(() => new DummyConnection()) {
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

      val props = ConnectionStateManager.props(
        cfg.copy(
          connectTimeout = 1.second,
          minReconnect = 3.seconds
        ),
        probe.ref,
        delayedFirstConnect
      )

      val csm = TestActorRef[ConnectionStateManager](props)

      csm ! CheckConnection(false)

      probe.fishForMessage(3.seconds) {
        case sc : ConnectionStateChanged => sc.state.status == ConnectionState.CONNECTING
        case _ => false
      }

      probe.fishForMessage(3.seconds) {
        case sc : ConnectionStateChanged => sc.state.status == ConnectionState.CONNECTING
        case _ => false
      }

      probe.fishForMessage(10.seconds) {
        case sc : ConnectionStateChanged => sc.state.status == ConnectionState.CONNECTED
        case _ => false
      }
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

      val props = ConnectionStateManager.props(
        cfg.copy(
          connectTimeout = 1.second,
          minReconnect = 1.seconds,
          maxReconnectTimeout = Some(5.seconds)
        ),
        probe.ref,
        neverConnect
      )

      val csm = TestActorRef[ConnectionStateManager](props)

      csm ! CheckConnection(false)

      probe.fishForMessage(10.seconds) {
        case _ : RestartContainer => true
        case _ => false
      }
    }

    "should initiate a container restart if the connection can't be established from connecting state" in {
      val onlyConnectOnce = new DummyHolder(() => new DummyConnection()) {
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

      val props = ConnectionStateManager.props(
        cfg.copy(
          connectTimeout = 1.second,
          minReconnect = 2.seconds,
          maxReconnectTimeout = Some(10.seconds)
        ),
        probe.ref,
        onlyConnectOnce
      )

      val csm = TestActorRef[ConnectionStateManager](props)

      csm ! CheckConnection(false)

      probe.fishForMessage(3.seconds) {
        case sc : ConnectionStateChanged => sc.state.status == ConnectionState.CONNECTING
        case _ => false
      }

      probe.fishForMessage(3.seconds) {
        case sc : ConnectionStateChanged => sc.state.status == ConnectionState.CONNECTED
        case _ => false
      }

      csm ! Disconnect(1.second)

      probe.fishForMessage(3.seconds) {
        case sc : ConnectionStateChanged => sc.state.status == ConnectionState.DISCONNECTED
        case _ => false
      }

      probe.fishForMessage(20.seconds) {
        case _ : RestartContainer => true
        case _ => false
      }
    }
  }
}
