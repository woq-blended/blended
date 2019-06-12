package blended.jmx.json

import blended.jmx.{JmxAttribute, JmxObject, JmxObjectName}
import prickle.{Pickler, Unpickler}

object PrickleProtocol {

  implicit val jmxObjectNamePickler : Pickler[JmxObjectName] = Pickler.materializePickler[JmxObjectName]
  implicit val jmxObjectNameUnpickler : Unpickler[JmxObjectName] = Unpickler.materializeUnpickler[JmxObjectName]

  implicit val jmxObjectPickler : Pickler[JmxObject] = Pickler.materializePickler[JmxObject]
  implicit val jmxObjectUnpickler : Unpickler[JmxObject] = Unpickler.materializeUnpickler[JmxObject]

  implicit val jmxAttributePickler : Pickler[JmxAttribute] = Pickler.materializePickler[JmxAttribute]
  implicit val jmxAttributeUnpickler : Unpickler[JmxAttribute] = Unpickler.materializeUnpickler[JmxAttribute]
}
