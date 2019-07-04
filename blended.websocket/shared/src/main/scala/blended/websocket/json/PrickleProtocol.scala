package blended.websocket.json

import blended.websocket.{BlendedJmxMessage, BlendedWsMessage, JmxSubscribe, JmxUnsubscribe, JmxUpdate, Version, VersionResponse, WsMessageEncoded}
import prickle._
import blended.jmx.json.PrickleProtocol._

object PrickleProtocol {

  implicit val envelopePickler : Pickler[WsMessageEncoded] = Pickler.materializePickler[WsMessageEncoded]
  implicit val envelopeUnpickler : Unpickler[WsMessageEncoded] = Unpickler.materializeUnpickler[WsMessageEncoded]

  implicit val versionPickler : Pickler[Version] = Pickler.materializePickler[Version]
  implicit val versionUnpickler : Unpickler[Version] = Unpickler.materializeUnpickler[Version]

  implicit val wsMessagesPicklerPair : PicklerPair[BlendedWsMessage] = CompositePickler[BlendedWsMessage]
    .concreteType[Version]
    .concreteType[VersionResponse]

  implicit val jmxSubscribePickler : Pickler[JmxSubscribe] = Pickler.materializePickler[JmxSubscribe]
  implicit val jmxSubscribeUnpickler : Unpickler[JmxSubscribe] = Unpickler.materializeUnpickler[JmxSubscribe]

  implicit val jmxUpdatePickler : Pickler[JmxUpdate] = Pickler.materializePickler[JmxUpdate]
  implicit val JmxUpdateUnpickler : Unpickler[JmxUpdate] = Unpickler.materializeUnpickler[JmxUpdate]

  implicit val jmxMessagesPicklerPair : PicklerPair[BlendedJmxMessage] = CompositePickler[BlendedJmxMessage]
    .concreteType[JmxSubscribe]
    .concreteType[JmxUnsubscribe]
    .concreteType[JmxUpdate]
}
