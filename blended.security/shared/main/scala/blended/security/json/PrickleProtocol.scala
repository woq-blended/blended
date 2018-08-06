package blended.security.json

import blended.security.BlendedPermission
import microjson.JsValue
import prickle.{JsConfig, PConfig, Pickler}

object PrickleProtocol {

  implicit val prickleConfig: PConfig[JsValue] = JsConfig(areSharedObjectsSupported = false)

  implicit val blendedPermissionPickler : Pickler[BlendedPermission] = Pickler.materializePickler[BlendedPermission]
}
