package blended.updater.remote

import blended.updater.config.RuntimeConfig

import scala.collection.immutable

/**
 * Persistence handling for [RuntimeConfig]s.
 */
trait RuntimeConfigPersistor {

  def persistRuntimeConfig(runtimeConfig: RuntimeConfig): Unit

  def findRuntimeConfigs(): immutable.Seq[RuntimeConfig]

}
