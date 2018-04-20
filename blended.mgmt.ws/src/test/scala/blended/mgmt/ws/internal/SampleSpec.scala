package blended.mgmt.ws.internal

import akka.http.scaladsl.model.ws.TextMessage
import akka.http.scaladsl.testkit.{ScalatestRouteTest, WSProbe}
import org.scalatest.FreeSpec

import scala.concurrent.duration._

class SampleSpec extends FreeSpec
  with ScalatestRouteTest {

  val server = new SimpleWebSocketServer()

  "The stuff should" - {

    "simply work" in {

      val wsClient : WSProbe = WSProbe()

      def expectTimerMessage(client : WSProbe) : Unit = {
        client .expectMessage() match {
          case tm : TextMessage.Strict =>
            assert(tm.text.startsWith("TimerEvent"))
          case _ => fail("Unexpected Message")
        }
      }

      WS("/timer?name=test", wsClient.flow) ~> server.route ~>
      check {
        assert(isWebSocketUpgrade)

        expectTimerMessage(wsClient)
        expectTimerMessage(wsClient)
        expectTimerMessage(wsClient)

        wsClient.sendCompletion()
        wsClient.expectCompletion()

        wsClient.expectNoMessage(3.seconds)
      }
    }
  }

}
