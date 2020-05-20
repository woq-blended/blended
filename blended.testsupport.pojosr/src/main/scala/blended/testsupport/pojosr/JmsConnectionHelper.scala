package blended.testsupport.pojosr

import akka.actor.ActorSystem
import akka.testkit.TestProbe
import blended.jms.utils._
import javax.jms.Connection

import scala.concurrent.duration._
import scala.util.Try

trait JmsConnectionHelper { this : PojoSrTestHelper =>

  def jmsConnectionFactory(r : BlendedPojoRegistry, mustConnect : Boolean = false, timeout : FiniteDuration = 3.seconds) : Try[IdAwareConnectionFactory] =
    namedJmsConnectionFactory(r, mustConnect = mustConnect, timeout = timeout, evalFilter = false)("", "")

  def namedJmsConnectionFactory(r : BlendedPojoRegistry, mustConnect : Boolean = false, timeout : FiniteDuration = 3.seconds, evalFilter : Boolean = true)(
    vendor : String, provider : String
  ) : Try[IdAwareConnectionFactory] = Try {
    implicit val to : FiniteDuration = timeout

    val filter : Option[String] = if (evalFilter) {
      Some(s"(&(vendor=$vendor)(provider=$provider))")
    } else {
      None
    }

    val cf : IdAwareConnectionFactory = mandatoryService[IdAwareConnectionFactory](r, filter)

    if (mustConnect) {
      ensureConnection(r)(cf, timeout).get
    }

    cf
  }

  def ensureConnection(r : BlendedPojoRegistry)(cf : IdAwareConnectionFactory, timeout: FiniteDuration = 3.seconds) : Try[Connection] = Try {
    implicit val to : FiniteDuration = timeout
    implicit val system : ActorSystem = mandatoryService[ActorSystem](r)

    val probe : TestProbe = TestProbe()
    system.eventStream.subscribe(probe.ref, classOf[ConnectionStateChanged])
    system.eventStream.publish(QueryConnectionState(cf.vendor, cf.provider))
    probe.fishForMessage(timeout){
      case ConnectionStateChanged(state) =>
        state.vendor == cf.vendor && state.provider == cf.provider && state.status == Connected
    }

    cf.createConnection()
  }
}
