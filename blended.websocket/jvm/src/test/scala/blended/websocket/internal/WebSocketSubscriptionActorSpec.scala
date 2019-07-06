package blended.websocket.internal

import java.lang.management.ManagementFactory

import akka.actor.PoisonPill
import akka.testkit.TestProbe
import blended.jmx.BlendedMBeanServerFacade
import blended.jmx.internal.BlendedMBeanServerFacadeImpl
import blended.util.RichTry._
import blended.websocket._
import blended.websocket.json.PrickleProtocol._

import scala.concurrent.duration._

class WebSocketSubscriptionActorSpec extends AbstractWebSocketSpec {

  private val mbf : BlendedMBeanServerFacade = new BlendedMBeanServerFacadeImpl(
    ManagementFactory.getPlatformMBeanServer()
  )

  private val interval : FiniteDuration = 200.millis

  "The Subscription actor should" - {

    def withJmxSubscription(sub : JmxSubscribe)(f : TestProbe => Unit) : Unit = {
      withWebSocketServer {

        val wsProbe : TestProbe = TestProbe()

        withWebsocketConnection("de_test", "secret", wsProbe.ref) { _ => token =>
          val subscription : WebsocketSubscription[BlendedJmxMessage] = WebsocketSubscription[BlendedJmxMessage](
            context = WsContext("jmx", "subscribe"),
            token = token,
            interval = if (sub.intervalMS <= 0) None else Some(sub.intervalMS.millis),
            pickler = jmxMessagePicklerPair.pickler,
            cmd = sub.asInstanceOf[BlendedJmxMessage],
            update = {
              case s @ JmxSubscribe(objName, _) => JmxUpdate(mbf.allMbeanNames().unwrap, Seq.empty)
            }
          )

          val s = system.actorOf(WebSocketSubscriptionActor.props[BlendedJmxMessage](subscription))

          fishForWsUpdate[BlendedJmxMessage](1.second)(wsProbe){ upd =>
            upd.isInstanceOf[JmxUpdate]
          }

          f(wsProbe)

          s ! PoisonPill
        }
      }
    }

    "emit a single message if it is created with a subscription without interval" in {
      withJmxSubscription(JmxSubscribe(None, 0L)) { probe => probe.expectNoMessage(1.second) }
    }

    "emit regular updates if an interval is set" in {
      withJmxSubscription(JmxSubscribe(None, interval.toMillis)) { probe =>
        probe.expectNoMessage((interval.toMillis * 0.9).millis)

        1.to(10).foreach { _ =>
          fishForWsUpdate[BlendedJmxMessage](interval + (interval.toMillis/2).millis)(probe){ upd =>
            upd.isInstanceOf[JmxUpdate]
          }
        }
      }
    }
  }
}
