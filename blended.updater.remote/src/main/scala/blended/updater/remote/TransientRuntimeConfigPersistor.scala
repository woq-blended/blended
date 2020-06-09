package blended.updater.remote

import scala.collection.immutable

import blended.updater.config.Profile

class TransientRuntimeConfigPersistor extends RuntimeConfigPersistor {

  private[this] var state: immutable.Set[Profile] = Set.empty

  override def persistRuntimeConfig(rCfg: Profile): Unit = state += rCfg

  override def findRuntimeConfigs(): List[Profile] = state.toList
}
