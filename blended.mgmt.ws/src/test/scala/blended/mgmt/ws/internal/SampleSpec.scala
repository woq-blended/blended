package blended.mgmt.ws.internal

import akka.http.scaladsl.testkit.{ScalatestRouteTest, WSProbe}
import org.scalatest.FreeSpec

class SampleSpec extends FreeSpec
  with ScalatestRouteTest {

  val server = new SimpleWebSocketServer()

  "The stuff should" - {

    "simply work" in {

      val wsClient = WSProbe()

      WS("/echo", wsClient.flow) ~> server.route ~>
      check {
        assert(isWebSocketUpgrade)

        wsClient.sendMessage("Andreas")
        wsClient.expectMessage("Andreas")

        wsClient.sendCompletion()
        wsClient.expectCompletion()
      }
    }
  }

}
