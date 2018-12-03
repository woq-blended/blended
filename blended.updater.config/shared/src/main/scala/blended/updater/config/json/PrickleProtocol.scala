package blended.updater.config.json

import blended.updater.config.ActivateProfile
import blended.updater.config.AddOverlayConfig
import blended.updater.config.AddRuntimeConfig
import blended.updater.config.ContainerInfo
import blended.updater.config.OverlayConfig
import blended.updater.config.OverlayState
import blended.updater.config.RuntimeConfig
import blended.updater.config.ServiceInfo
import blended.updater.config.StageProfile
import blended.updater.config.UpdateAction
import prickle._
import microjson.JsValue

/**
 * Import this object to have Prickle JSON pickers and unpicklers in implicit scope.
 */
object PrickleProtocol {

  implicit val prickleConfig: PConfig[JsValue] = JsConfig(areSharedObjectsSupported = false)

  // sealed trait needs special handling
  implicit val updateActionPickler: PicklerPair[UpdateAction] = CompositePickler[UpdateAction].
    concreteType[ActivateProfile].
    concreteType[StageProfile].
    concreteType[AddRuntimeConfig].
    concreteType[AddOverlayConfig]

  // Workaround for scala compiler error "diverging implicit expansion"
  // see https://github.com/benhutchison/prickle/issues/27
  implicit val serviceInfoPickler: Pickler[ServiceInfo] = Pickler.materializePickler[ServiceInfo]
  implicit val serviceInfoUnpickler: Unpickler[ServiceInfo] = Unpickler.materializeUnpickler[ServiceInfo]

  implicit val containerInfoPickler: Pickler[ContainerInfo] = Pickler.materializePickler[ContainerInfo]
  implicit val containerInfoUnpickler: Unpickler[ContainerInfo] = Unpickler.materializeUnpickler[ContainerInfo]

  implicit val overlayStatePickler: PicklerPair[OverlayState] = CompositePickler[OverlayState].
    concreteType[OverlayState.Active.type].
    concreteType[OverlayState.Valid.type].
    concreteType[OverlayState.Invalid.type].
    concreteType[OverlayState.Pending.type]

  implicit val runtimeConfigPickler: Pickler[RuntimeConfig] = Pickler.materializePickler[RuntimeConfig]
  implicit val runtimeConfigUnpickler: Unpickler[RuntimeConfig] = Unpickler.materializeUnpickler[RuntimeConfig]

  implicit val overlayConfigPickler: Pickler[OverlayConfig] = Pickler.materializePickler[OverlayConfig]
  implicit val overlayConfigUnpickler: Unpickler[OverlayConfig] = Unpickler.materializeUnpickler[OverlayConfig]

}
