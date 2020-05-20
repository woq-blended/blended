package blended.websocket.internal

import java.lang.management.ManagementFactory

import akka.actor.{ActorSystem, PoisonPill}
import akka.stream.{ActorMaterializer, Materializer}
import akka.testkit.TestProbe
import blended.jmx.internal.BlendedMBeanServerFacadeImpl
import blended.jmx.{BlendedMBeanServerFacade, JmxObjectName}
import blended.security.login.api.Token
import blended.util.RichTry._
import blended.websocket._
import blended.websocket.json.PrickleProtocol._
import prickle.Pickler

import scala.concurrent.duration._
import scala.util.Try

class WebSocketSubscriptionActorSpec extends AbstractWebSocketSpec {

  private val mbf : BlendedMBeanServerFacade = new BlendedMBeanServerFacadeImpl(
    ManagementFactory.getPlatformMBeanServer()
  )

  private val interval : FiniteDuration = 200.millis

  "The Subscription actor should" - {

    def withJmxSubscription(sub : JmxSubscribe)(f : TestProbe => Unit) : Unit = {
      withWebSocketServer {
        implicit val system: ActorSystem = actorSystem
        implicit val materializer: Materializer = ActorMaterializer()

        val wsProbe : TestProbe = TestProbe()

        withWebsocketConnection("de_test", "secret", wsProbe.ref) { _ => t =>
          val subscription : WebSocketSubscription = new WebSocketSubscription {

            override type T = BlendedJmxMessage

            override val context : WsContext = WsContext("jmx", "subscribe")
            override val token : Token = t
            override val interval : Option[FiniteDuration] = if (sub.intervalMS <= 0) None else Some(sub.intervalMS.millis)
            override val pickler : Pickler[BlendedJmxMessage] = jmxMessagePicklerPair.pickler
            override val cmd : BlendedJmxMessage = sub.asInstanceOf[BlendedJmxMessage]
            override val update : PartialFunction[T,Try[T]] = JmxCommandPackage.jmxUpdate(mbf)
          }

          val s = system.actorOf(WebSocketSubscriptionActor.props[BlendedJmxMessage](subscription))

          fishForWsUpdate[BlendedJmxMessage](1.second)(wsProbe){ upd =>
            upd.isInstanceOf[JmxUpdate] && upd.asInstanceOf[JmxUpdate].beans.nonEmpty
          }

          f(wsProbe)

          s ! PoisonPill
        }
      }
    }

    "emit a single message if it is created with a subscription without interval" in {
      withJmxSubscription(JmxSubscribe(Some(JmxObjectName("java.lang:type=Memory").unwrap), 0L)) { probe => probe.expectNoMessage(1.second) }
    }

    "emit regular updates if an interval is set" in {
      withJmxSubscription(JmxSubscribe(Some(JmxObjectName("java.lang:type=Memory").unwrap), interval.toMillis)) { probe =>
        probe.expectNoMessage((interval.toMillis * 0.7).millis)

        // scalastyle:off magic.number
        1.to(10).foreach { i =>
          fishForWsUpdate[BlendedJmxMessage](interval + (interval.toMillis/2).millis)(probe){ upd =>
            upd.isInstanceOf[JmxUpdate] &&
            upd.asInstanceOf[JmxUpdate].beans.nonEmpty
          }
        }
        // scalastyle:on magic.number
      }
    }
  }
}
