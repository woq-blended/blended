package blended.updater.remote

import blended.updater.config.OverlayConfig

import scala.collection.immutable

class TransientOverlayConfigPersistor extends OverlayConfigPersistor {

  private[this] var state : immutable.Set[OverlayConfig] = Set()

  override def persistOverlayConfig(overlayConfig : OverlayConfig) : Unit = state += overlayConfig

  override def findOverlayConfigs() : List[OverlayConfig] = state.toList

}
