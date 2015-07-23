package blended.updater

import com.typesafe.config.Config

object UpdaterConfig {

  implicit class ConfigWithDefaults(val cfg: Config) extends AnyVal {
    def getInt(key: String, default: Int): Int = if (cfg.hasPath(key)) cfg.getInt(key) else default
    def getLong(key: String, default: Long): Long = if (cfg.hasPath(key)) cfg.getLong(key) else default
  }

  val default: UpdaterConfig = {
    UpdaterConfig(
      artifactDownloaderPoolSize = 4,
      artifactCheckerPoolSize = 4,
      unpackerPoolSize = 4,
      autoStagingDelayMSec = 0,
      autoStagingIntervalMSec = 0
    )

  }

  def fromConfig(cfg: Config): UpdaterConfig = {
    UpdaterConfig(
      artifactDownloaderPoolSize = cfg.getInt("artifactDownloaderPoolSize", default.artifactDownloaderPoolSize),
      artifactCheckerPoolSize = cfg.getInt("artifactCheckerPoolSize", default.artifactCheckerPoolSize),
      unpackerPoolSize = cfg.getInt("updaterPoolSize", default.unpackerPoolSize),
      autoStagingDelayMSec = cfg.getLong("autoStagingDelayMSec", default.autoStagingDelayMSec),
      autoStagingIntervalMSec = cfg.getLong("autoStagingIntervalMSec", default.autoStagingIntervalMSec)
    )
  }
}

case class UpdaterConfig(
  artifactDownloaderPoolSize: Int,
  artifactCheckerPoolSize: Int,
  unpackerPoolSize: Int,
  autoStagingDelayMSec: Long,
  autoStagingIntervalMSec: Long)
    