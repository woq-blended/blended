package blended.updater.remote

import blended.updater.config.RuntimeConfig

import scala.collection.immutable

class TransientRuntimeConfigPersistor extends RuntimeConfigPersistor {

  private[this] var state: immutable.Set[RuntimeConfig] = Set()

  override def persistRuntimeConfig(runtimeConfig: RuntimeConfig): Unit = state += runtimeConfig

  override def findRuntimeConfigs(): immutable.Seq[RuntimeConfig] = state.to[immutable.Seq]
}
