package blended.updater.internal

import blended.updater.RuntimeConfig
import blended.updater.RuntimeConfigRepository
import java.io.File
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
import scala.collection.immutable._
import scala.collection.JavaConverters._
import java.io.BufferedOutputStream
import java.io.FileOutputStream
import java.io.PrintStream
import com.typesafe.config.ConfigRenderOptions

class FileBasedRuntimeConfigRepository(configFile: File, configListPrefix: String) extends RuntimeConfigRepository {

  def readConfigs(): Seq[RuntimeConfig] = {
    val config = ConfigFactory.parseFile(configFile)
    if (config.hasPath(configListPrefix)) {
      config.getConfigList(configListPrefix).asScala.toList.map { config =>
        RuntimeConfig.read(config)
      }
    } else Seq()
  }

  def writeConfigs(runtimeConfigs: Seq[RuntimeConfig]): Unit = {
    val config = ConfigFactory.parseFile(configFile)
    val updatedConfig = config.withValue(configListPrefix, ConfigValueFactory.fromIterable(runtimeConfigs.map {
      rc => RuntimeConfig.toConfig(rc).root().unwrapped()
    }.asJava))
    val os = new PrintStream(new BufferedOutputStream(new FileOutputStream(configFile)))
    try {
      os.print(updatedConfig.root().render(ConfigRenderOptions.concise().setFormatted(true)))
    } finally {
      os.close()
    }
  }

  private[this] var configs: Seq[RuntimeConfig] = readConfigs()

  override def getAll(): Seq[RuntimeConfig] = configs

  override def add(runtimeConfig: RuntimeConfig): Unit = {
    configs = (runtimeConfig +: configs).distinct
    writeConfigs(configs)
  }
  override def remove(name: String, version: String): Unit = {
    configs = configs.filter(c => c.name != name || c.version != version)
    writeConfigs(configs)
  }
}
