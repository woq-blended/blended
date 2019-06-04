package blended.updater.remote

import blended.updater.config.RuntimeConfig

import scala.collection.immutable

class TransientRuntimeConfigPersistor extends RuntimeConfigPersistor {

  private[this] var state : immutable.Set[RuntimeConfig] = Set.empty

  override def persistRuntimeConfig(rCfg : RuntimeConfig) : Unit = state += rCfg

  override def findRuntimeConfigs() : List[RuntimeConfig] = state.toList
}
