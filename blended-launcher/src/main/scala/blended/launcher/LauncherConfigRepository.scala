package blended.launcher

import blended.updater.config.LauncherConfig

trait LauncherConfigRepository {
  def getCurrentConfig(): Option[LauncherConfig]
  def updateConfig(config: LauncherConfig): Unit
}

class DummyLauncherConfigRepository(initialConfig: Option[LauncherConfig] = None) extends LauncherConfigRepository {
  private[this] var currentConfig: Option[LauncherConfig] = initialConfig
  override def getCurrentConfig(): Option[LauncherConfig] = currentConfig
  override def updateConfig(config: LauncherConfig): Unit = currentConfig = Option(config)
}
