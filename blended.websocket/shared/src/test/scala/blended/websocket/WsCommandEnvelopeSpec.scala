package blended.websocket

import blended.websocket.json.PrickleProtocol._
import org.scalatest.{FreeSpec, Matchers}
import prickle._

class WsCommandEnvelopeSpec extends FreeSpec
  with Matchers {

  private def simpleDecodeTest(cmd : StringCommandEnvelope.WsCommand) : Unit = {
    val encoded : WsMessageEncoded = StringCommandEnvelope.encode(cmd)
    val json : String = Pickle.intoString(encoded)
    val decoded : WsMessageEncoded = Unpickle[WsMessageEncoded].fromString(json).get
    val cmd2 : StringCommandEnvelope.WsCommand = StringCommandEnvelope.decode(decoded).get
    cmd2 should be (cmd)
  }

  "A WsCommandEnvelope should" - {

    "encode / decode to/from Json correctly" in {

      simpleDecodeTest(StringCommandEnvelope.WsCommand(
        namespace = "testNs",
        name = "test",
        content = "my cool command"
      ))

      simpleDecodeTest(StringCommandEnvelope.WsCommand(
        namespace = "testNs",
        name = "test",
        content = "my cool command",
        // scalastyle:off magic.number
        status = Some(200),
        // scalastyle:on magic.number
        statusMsg = Some("This worked")
      ))
    }
  }
}
