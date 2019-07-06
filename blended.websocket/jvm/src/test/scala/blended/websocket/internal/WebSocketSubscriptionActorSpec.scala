package blended.websocket.internal

import java.lang.management.ManagementFactory

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

  "The Subscription actor should" - {


    "emit a single message if it is created with a subscription without interval" in {

      withWebSocketServer {

        val wsProbe : TestProbe = TestProbe()

        withWebsocketConnection("de_test", "secret", wsProbe.ref) { _ => token =>
          val subscription : WebsocketSubscription[BlendedJmxMessage] = WebsocketSubscription[BlendedJmxMessage](
            context = WsContext("jmx", "subscribe"),
            token = token,
            interval = None,
            cmd = JmxSubscribe(None, 0L),
            update = {
              case s @ JmxSubscribe(objName, _) => JmxUpdate(mbf.allMbeanNames().unwrap, Seq.empty)
            }
          )

          val s = system.actorOf(WebSocketSubscriptionActor.props[BlendedJmxMessage](subscription))

          fishForWsUpdate[BlendedJmxMessage](1.second)(wsProbe){ upd =>
            upd.isInstanceOf[JmxUpdate]
          }

          wsProbe.expectNoMessage(1.second)
        }
      }

    }
  }
}
