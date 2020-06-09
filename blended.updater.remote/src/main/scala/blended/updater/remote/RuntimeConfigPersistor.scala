package blended.updater.remote

import blended.updater.config.Profile

/**
 * Persistence handling for [RuntimeConfig]s.
 */
trait RuntimeConfigPersistor {

  def persistRuntimeConfig(runtimeConfig : Profile) : Unit

  def findRuntimeConfigs() : List[Profile]

}
