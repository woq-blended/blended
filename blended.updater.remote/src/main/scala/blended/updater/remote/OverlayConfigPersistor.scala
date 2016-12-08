package blended.updater.remote

import blended.updater.config.OverlayConfig

trait OverlayConfigPersistor {

  def persistOverlayConfig(overlayConfig: OverlayConfig): Unit

  def findOverlayConfigs(): List[OverlayConfig]

}
