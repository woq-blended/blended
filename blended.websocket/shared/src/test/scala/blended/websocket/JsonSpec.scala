package blended.websocket

import blended.websocket.json.PrickleProtocol._
import org.scalatest.{FreeSpec, Matchers}
import prickle._

class JsonSpec extends FreeSpec
  with Matchers {

  def decodeTest[T](t: T)(implicit p: Pickler[T], up: Unpickler[T]): T = {
    val json: String = JsonHelper.encode(t)
    val v = JsonHelper.decode[T](json).get
    v should be(t)

    v
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
      val v: BlendedWsMessage = Version()
      decodeTest(v)
    }

    "encode / decode to/from Json correctly (VersionResponse)" in {
      val v: BlendedWsMessage = VersionResponse("foo")
      decodeTest(v)
    }
  }

  "A WsMessageEncoded should" - {

    "encode / decode to/from Json correctly (Context)" in {
      val v: WsMessageEncoded = WsMessageEncoded.fromContext(WsContext(namespace = "foo", name = "bar"))
      decodeTest(v)
    }

    "encode / decode to/from Json correctly (object)" in {
      val obj : BlendedWsMessage = VersionResponse("V1")
      val v: WsMessageEncoded = WsMessageEncoded.fromObject(WsContext(namespace = "foo", name = "bar"), obj)
      val v2 : WsMessageEncoded = decodeTest(v)

      v2.decode[BlendedWsMessage].get should be (obj)
    }

  }
}
