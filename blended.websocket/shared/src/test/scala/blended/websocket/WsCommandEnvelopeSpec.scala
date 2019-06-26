package blended.websocket

import org.scalatest.{FreeSpec, Matchers}
import prickle.{Pickler, Unpickler}

class WsCommandEnvelopeSpec extends FreeSpec
  with Matchers {

  "A WsCommandEnvelope should" - {

    def decodeTest[T](t: T)(implicit p : Pickler[T], up : Unpickler[T]) : Unit = {
      val json : String = JsonHelper.encode(t)
      val v = JsonHelper.decode[T](json).get
      v should be (t)
    }

    "encode / decode to/from Json correctly (String)" in {
      decodeTest("my cool command")
    }

    "encode / decode to/from Json correctly (Unit)" in {
      decodeTest(())
    }
  }
}
