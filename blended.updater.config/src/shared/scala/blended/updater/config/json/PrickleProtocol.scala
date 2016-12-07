package blended.updater.config.json

import blended.updater.config._
import prickle._

object PrickleProtocol {

  implicit val updateActionPickler: PicklerPair[UpdateAction] = CompositePickler[UpdateAction].
    concreteType[ActivateProfile].
    concreteType[StageProfile].
    concreteType[AddRuntimeConfig].
    concreteType[AddOverlayConfig]

  // Workaround for scala compiler error "diverging implicit expansion"
  // see https://github.com/benhutchison/prickle/issues/27
  implicit val serviceInfoPickler: Pickler[ServiceInfo] = Pickler.materializePickler[ServiceInfo]
  implicit val serviceInfoUnpickler: Unpickler[ServiceInfo] = Unpickler.materializeUnpickler[ServiceInfo]

  implicit val overlayStatePickler: PicklerPair[OverlayState] = CompositePickler[OverlayState].
    concreteType[OverlayState.Active.type].
    concreteType[OverlayState.Valid.type].
    concreteType[OverlayState.Invalid.type].
    concreteType[OverlayState.Pending.type]

}