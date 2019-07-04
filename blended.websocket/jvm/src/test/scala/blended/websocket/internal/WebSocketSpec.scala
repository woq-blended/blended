package blended.websocket.internal

import akka.http.scaladsl.Http
import akka.http.scaladsl.model.ws.{TextMessage, WebSocketRequest}
import akka.http.scaladsl.model.{StatusCodes => AkkaStatusCodes}
import akka.stream.scaladsl.Keep
import akka.testkit.TestProbe
import blended.util.RichTry._
import blended.websocket._
import blended.websocket.json.PrickleProtocol._

import scala.concurrent.Await
import scala.concurrent.duration._

class WebSocketSpec extends AbstractWebSocketSpec {

  "The Web socket server should" - {

   "reject clients without token" in {
     withWebSocketServer {
       val flow = Http().webSocketClientFlow(WebSocketRequest("ws://localhost:9995/ws/"))

       val (resp, _) = source
         .viaMat(flow)(Keep.right)
         .toMat(incoming(TestProbe().ref))(Keep.both)
         .run()

       // if no token is given in the parameters, a 404 is returned
       val connected = Await.result(resp, 3.seconds)
       connected.response.status should be(AkkaStatusCodes.NotFound)
     }
   }

   "reject clients with a fantasy token" in {
     withWebSocketServer {
       val flow = Http().webSocketClientFlow(WebSocketRequest("ws://localhost:9995/ws/?token=foo"))

       val (resp, _) = source
         .viaMat(flow)(Keep.right)
         .toMat(incoming(TestProbe().ref))(Keep.both)
         .run()

       val connected = Await.result(resp, 3.seconds)
       connected.response.status should be(AkkaStatusCodes.Unauthorized)
     }
   }

   "accept clients with a real token" in {

     withWebSocketServer {
       // login and retrieve the token
       val token = login("bg_test", "secret").unwrap

       // set up the WS listener
       val ((_, resp), _) = source
         .viaMat(wsFlow(token.webToken))(Keep.both)
         .toMat(incoming(TestProbe().ref))(Keep.both)
         .run()

       // We are expecting a Switch Protocol result when the WS client is connected
       val connected = Await.result(resp, 3.seconds)
       connected.response.status should be(AkkaStatusCodes.SwitchingProtocols)
     }
   }

   "respond to a malformed web socket request with BAD_REQUEST" in {
     withWebSocketServer {

       val probe: TestProbe = TestProbe()

       withWebsocketConnection("bg_test", "secret", probe.ref) { actor =>
         actor ! TextMessage.Strict("Hello Blended")
         fishForWsUpdate[Unit](probe, AkkaStatusCodes.BadRequest){ _ => true}
       }
     }
   }

   "respond to an unknown command with a NOT_FOUND" in {

     withWebSocketServer {

       val probe: TestProbe = TestProbe()
       withWebsocketConnection("bg_test", "secret", probe.ref) { actor =>
         actor ! TextMessage.Strict(WsMessageEncoded.fromContext(WsContext(namespace = "foo", name = "bar")).json)
         fishForWsUpdate[Unit](probe, AkkaStatusCodes.NotFound){ _ => true}

         actor ! TextMessage.Strict(WsMessageEncoded.fromContext(WsContext(namespace = "blended", name = "doesNotExist")).json)
         fishForWsUpdate[Unit](probe, AkkaStatusCodes.NotFound){ _ => true}
       }
      }
    }

    "respond to a valid Web Socket request" in {
      withWebSocketServer {

          val probe: TestProbe = TestProbe()

          withWebsocketConnection("bg_test", "secret", probe.ref) { actor =>
            val blendedCmd : BlendedWsMessage = Version()

            val msg = WsMessageEncoded.fromObject(WsContext(namespace = "blended", name = "version"), blendedCmd)
            actor ! TextMessage.Strict(msg.json)

            fishForWsUpdate[Unit](probe) { _ => true }

            fishForWsUpdate[BlendedWsMessage](probe) { wsMsg =>
              wsMsg.isInstanceOf[VersionResponse] &&
              wsMsg.asInstanceOf[VersionResponse].v.equals(BlendedCommandPackage.version)
            }
          }
      }
    }
  }
}
