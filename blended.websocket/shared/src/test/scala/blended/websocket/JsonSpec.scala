package blended.websocket

import org.scalatest.{FreeSpec, Matchers}
import prickle._
import blended.websocket.json.PrickleProtocol._

class JsonSpec extends FreeSpec
  with Matchers {

  def decodeTest[T](t: T)(implicit p : Pickler[T], up : Unpickler[T]) : Unit = {
    val json : String = JsonHelper.encode(t)
    println(json)
    val v = JsonHelper.decode[T](json).get
    v should be (t)
  }

  "A WsCommandEnvelope should" - {

    "encode / decode to/from Json correctly (String)" in {
      decodeTest("my cool command")
    }

    "encode / decode to/from Json correctly (Unit)" in {
      decodeTest(())
    }
  }

  "A BlendedWsMessage should" - {

    "encode / decode to/from Json correctly (Version)" in {
      val v : BlendedWsMessages = Version()
      decodeTest(v)
    }

    "encode / decode to/from Json correctly (VersionResponse)" in {
      val v : BlendedWsMessages = VersionResponse("foo")
      decodeTest(v)
    }
  }
}
