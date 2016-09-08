package blended.updater.remote

import blended.updater.config.OverlayConfig

import scala.collection.immutable

trait OverlayConfigPersistor {

  def persistOverlayConfig(overlayConfig: OverlayConfig): Unit

  def findOverlayConfigs(): immutable.Seq[OverlayConfig]

}
