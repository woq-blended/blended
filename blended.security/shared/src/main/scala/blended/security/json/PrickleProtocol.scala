package blended.security.json

import blended.security.{BlendedPermission, BlendedPermissions}
import microjson.JsValue
import prickle.{JsConfig, PConfig, Pickler, Unpickler}

object PrickleProtocol {

  implicit val prickleConfig: PConfig[JsValue] = JsConfig(areSharedObjectsSupported = false)

  implicit val permissionPickler : Pickler[BlendedPermission] = Pickler.materializePickler[BlendedPermission]
  implicit val permissionUnpickler : Unpickler[BlendedPermission] = Unpickler.materializeUnpickler[BlendedPermission]

  implicit val permissionsPickler : Pickler[BlendedPermissions] = Pickler.materializePickler[BlendedPermissions]
  implicit val permissionsUnpickler : Unpickler[BlendedPermissions] = Unpickler.materializeUnpickler[BlendedPermissions]
}
