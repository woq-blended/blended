package blended.websocket.json

import blended.websocket.{BlendedWsMessages, Version, VersionResponse, WsMessageEncoded}
import prickle._

object PrickleProtocol {

  implicit val envelopePickler : Pickler[WsMessageEncoded] = Pickler.materializePickler[WsMessageEncoded]
  implicit val envelopeUnpickler : Unpickler[WsMessageEncoded] = Unpickler.materializeUnpickler[WsMessageEncoded]

  implicit val versionPickler : Pickler[Version] = Pickler.materializePickler[Version]
  implicit val versionUnpickler : Unpickler[Version] = Unpickler.materializeUnpickler[Version]

  implicit val wsMessagesPicklerPair : PicklerPair[BlendedWsMessages] = CompositePickler[BlendedWsMessages]
    .concreteType[Version]
    .concreteType[VersionResponse]
}
