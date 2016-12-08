package blended.updater.remote

import blended.updater.config.RuntimeConfig

/**
 * Persistence handling for [RuntimeConfig]s.
 */
trait RuntimeConfigPersistor {

  def persistRuntimeConfig(runtimeConfig: RuntimeConfig): Unit

  def findRuntimeConfigs(): List[RuntimeConfig]

}
