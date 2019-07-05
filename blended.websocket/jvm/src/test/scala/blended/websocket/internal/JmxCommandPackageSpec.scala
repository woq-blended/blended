package blended.websocket.internal

import akka.http.scaladsl.model.ws.TextMessage
import akka.testkit.TestProbe
import blended.websocket.json.PrickleProtocol._
import blended.websocket.{BlendedJmxMessage, JmxSubscribe, JmxUpdate, WsContext, WsMessageEncoded}
import scala.concurrent.duration._

class JmxCommandPackageSpec extends AbstractWebSocketSpec {

  "The Jmx Command package should" - {

    "Handle subscriptions for the JMX tree only" in {

      withWebSocketServer {

        val probe : TestProbe = TestProbe()

        withWebsocketConnection("de_test", "secret", probe.ref) { actor =>
          val subscribe : BlendedJmxMessage = JmxSubscribe(objName = None, intervalMS = 0L)
          val enc = WsMessageEncoded.fromObject(WsContext(namespace = "jmx", name = "subscribe"), subscribe)
          actor ! TextMessage.Strict(enc.json)

          fishForWsUpdate[Unit](probe)( _ => true)
          fishForWsUpdate[BlendedJmxMessage](probe) { m =>
            m.isInstanceOf[JmxUpdate] && {
              val upd: JmxUpdate = m.asInstanceOf[JmxUpdate]
              upd.names.nonEmpty && upd.beans.isEmpty
            }
          }

          probe.expectNoMessage(3.seconds)
        }
      }
    }
  }

}
