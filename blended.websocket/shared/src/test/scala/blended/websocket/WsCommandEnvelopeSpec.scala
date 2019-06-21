package blended.websocket

import org.scalatest.{FreeSpec, Matchers}
import prickle.{Pickler, Unpickler}

class WsCommandEnvelopeSpec extends FreeSpec
  with Matchers {

  private val ctxt : WsMessageContext = WsMessageContext(
    namespace = "testNs",
    name = "test"
  )

  "A WsCommandEnvelope should" - {

    def decodeTest[T](cmd: WsMessageEnvelope[T])(implicit p : Pickler[T], up : Unpickler[T]) : Unit = {
      val json : String = cmd.encode()
      val (c, s) = WsMessageEnvelope.decode[T](json).get
      c should be (cmd.context)
      s should be (cmd.content)
    }

    "encode / decode to/from Json correctly (String)" in {

      decodeTest(WsStringMessage(ctxt, "my cool command"))

      decodeTest(WsStringMessage(
        ctxt.copy(
          // scalastyle:off magic.number
          status = Some(200),
          // scalastyle:on magic.number
          statusMsg = Some("This worked")
        ),
        content = "my cool command"
      ))
    }

    "encode / decode to/from Json correctly (Unit)" in {
      decodeTest(WsUnitMessage(ctxt))
    }
  }
}
