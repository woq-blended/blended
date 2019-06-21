package blended.websocket.json

import blended.websocket.{WsMessageContext, WsMessageEncoded}
import prickle._

object PrickleProtocol {

  implicit val contextPickler : Pickler[WsMessageContext] = Pickler.materializePickler[WsMessageContext]
  implicit val contextUnpickler : Unpickler[WsMessageContext] = Unpickler.materializeUnpickler[WsMessageContext]

  implicit val envelopePickler : Pickler[WsMessageEncoded] = Pickler.materializePickler[WsMessageEncoded]
  implicit val envelopeUnpickler : Unpickler[WsMessageEncoded] = Unpickler.materializeUnpickler[WsMessageEncoded]
}
