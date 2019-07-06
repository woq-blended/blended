package blended.websocket.internal

import akka.http.scaladsl.model.ws.TextMessage
import akka.testkit.TestProbe
import blended.util.logging.Logger
import blended.websocket.json.PrickleProtocol._
import blended.websocket.{BlendedJmxMessage, JmxSubscribe, JmxUpdate, WsContext, WsMessageEncoded}

import scala.concurrent.duration._

class JmxCommandPackageSpec extends AbstractWebSocketSpec {

  private val log : Logger = Logger[JmxCommandPackageSpec]

  private val probeTime : FiniteDuration = 1.second
  private val interval : FiniteDuration = 1.second

  private val checkUpdatedNoBeans : BlendedJmxMessage => Boolean = { m =>
    m.isInstanceOf[JmxUpdate] && {
      val upd: JmxUpdate = m.asInstanceOf[JmxUpdate]
      log.info(s"Received Jmx Update [$upd]")
      upd.names.nonEmpty && upd.beans.isEmpty
    }
  }

  "The Jmx Command package should" - {

    def withjmxSubscription(sub : BlendedJmxMessage)(f : TestProbe => Unit) : Unit = {

      withWebSocketServer {

        val probe : TestProbe = TestProbe()

        withWebsocketConnection("de_test", "secret", probe.ref) { actor => _ =>
          val enc = WsMessageEncoded.fromObject(WsContext(namespace = "jmx", name = "subscribe"), sub)
          actor ! TextMessage.Strict(enc.json)

          // This is the response that the command has executed
          fishForWsUpdate[Unit](probeTime)(probe)( _ => true)
          f(probe)
        }
      }
    }

    "Handle subscriptions for the JMX tree only (no regular updates)" in {

      withjmxSubscription(JmxSubscribe(objName = None, intervalMS = 0L)) { probe =>
        fishForWsUpdate[BlendedJmxMessage](probeTime)(probe)(checkUpdatedNoBeans)
        probe.expectNoMessage(3.seconds)
      }
    }

    "Handle subscriptions for the JMX tree only (with regular updates)" in {

      withjmxSubscription(JmxSubscribe(objName = None, intervalMS = interval.toMillis)) { probe =>

        fishForWsUpdate[BlendedJmxMessage](probeTime)(probe)(checkUpdatedNoBeans)
        fishForWsUpdate[BlendedJmxMessage](interval * 2)(probe)(checkUpdatedNoBeans)
      }
    }
  }

}
