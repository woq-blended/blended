package blended.updater

import scala.collection.immutable._

trait RuntimeConfigRepository {
  def getAll(): Seq[RuntimeConfig]
  def getByName(name: String): Seq[RuntimeConfig] = getAll().filter { c => c.name == name }
  def getByNameAndVersion(name: String, version: String): Option[RuntimeConfig] = getByName(name).find { c => c.version == version }
  def add(runtimeConfig: RuntimeConfig): Unit
  def remove(name: String, version: String): Unit
}

class InMemoryRuntimeConfigRepository() extends RuntimeConfigRepository {
  private[this] var configs: Seq[RuntimeConfig] = Seq()
  override def getAll(): Seq[RuntimeConfig] = configs
  override def add(runtimeConfig: RuntimeConfig): Unit = configs = (runtimeConfig +: configs).distinct
  override def remove(name: String, version: String): Unit = configs = configs.filter(c => c.name != name && c.version != version)
}
