package blended.jmx.json

import blended.jmx.JmxObjectName
import prickle.{Pickler, Unpickler}

object PrickleProtocol {

  implicit val jmxObjectNamePickler : Pickler[JmxObjectName] = Pickler.materializePickler[JmxObjectName]
  implicit val jmxObjectNameUnpickler : Unpickler[JmxObjectName] = Unpickler.materializeUnpickler[JmxObjectName]

}
