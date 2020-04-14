package blended.testsupport.pojosr

import akka.actor.ActorSystem
import akka.testkit.TestProbe
import blended.jms.utils._
import javax.jms.Connection

import scala.concurrent.duration._
import scala.util.Try

trait EnsureJmsConnectivity {

  def ensureConnection(cf : IdAwareConnectionFactory, timeout: FiniteDuration = 3.seconds)(implicit system : ActorSystem) : Try[Connection] = Try {
    val probe : TestProbe = TestProbe()
    system.eventStream.subscribe(probe.ref, classOf[ConnectionStateChanged])
    system.eventStream.publish(QueryConnectionState("activemq", "activemq"))
    probe.fishForMessage(timeout){
      case ConnectionStateChanged(state) =>
        state.vendor == "activemq" && state.provider == "activemq" && state.status == Connected
    }

    cf.createConnection()
  }
}
