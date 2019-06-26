package blended.websocket.json

import blended.websocket.WsMessageEncoded
import prickle._

object PrickleProtocol {

  implicit val envelopePickler : Pickler[WsMessageEncoded] = Pickler.materializePickler[WsMessageEncoded]
  implicit val envelopeUnpickler : Unpickler[WsMessageEncoded] = Unpickler.materializeUnpickler[WsMessageEncoded]
}
