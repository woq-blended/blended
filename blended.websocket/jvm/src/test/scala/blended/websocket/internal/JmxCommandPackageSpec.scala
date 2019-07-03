package blended.websocket.internal

import akka.http.scaladsl.model.ws.TextMessage
import akka.testkit.TestProbe
import blended.websocket.json.PrickleProtocol._
import blended.websocket.{BlendedJmxMessage, JmxSubscribe, WsContext, WsMessageEncoded}

class JmxCommandPackageSpec extends AbstractWebSocketSpec {

  "The Jmx Command package should" - {

    "Handle subscriptions for the JMX tree only" in {

      withWebSocketServer {

        val probe : TestProbe = TestProbe()

        withWebsocketConnection("de_test", "secret", probe.ref) { actor =>
          val subscribe : BlendedJmxMessage = JmxSubscribe(None)
          val enc = WsMessageEncoded.fromObject(WsContext("jmx", "subscribe"), subscribe)
          actor ! TextMessage.Strict(enc.json)

          fishForWsUpdate[Unit](probe)( _ => true)
        }

      }
    }
  }

}
