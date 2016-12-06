package blended.updater.remote

import java.io.File

import blended.updater.config.{ConfigWriter, RuntimeConfig, RuntimeConfigCompanion}
import com.typesafe.config.ConfigFactory
import org.slf4j.LoggerFactory

import scala.util.Try

class FileSystemRuntimeConfigPersistor(storageDir: File) extends RuntimeConfigPersistor {

  private[this] val log = LoggerFactory.getLogger(classOf[FileSystemRuntimeConfigPersistor])

  private[this] var runtimeConfigs: Map[File, RuntimeConfig] = Map()
  private[this] var initialized: Boolean = false

  def fileName(rc: RuntimeConfig): String = s"${rc.name}-${rc.version}.conf"

  def initialize(): Unit = {
    log.debug("About to initialize runtime config persistor for storageDir: {}", storageDir)
    runtimeConfigs = if (!storageDir.exists()) {
      Map()
    } else {
      val files = Option(storageDir.listFiles()).getOrElse(Array())
      val rcs = files.flatMap { file =>
        val rc = Try {
          ConfigFactory.parseFile(file).resolve()
        }.flatMap { rawConfig =>
          RuntimeConfigCompanion.read(rawConfig)
        }
        log.debug("Found file: {} with: {}", Array(file, rc))
        rc.toOption.map(rc => file -> rc)
      }
      rcs.filter { case (file, rc) => file.getName() == fileName(rc) }.toMap
    }
    initialized = true
  }

  override def persistRuntimeConfig(runtimeConfig: RuntimeConfig): Unit = {
    if (!initialized) initialize()
    val configFile = new File(storageDir, fileName(runtimeConfig))
    if (configFile.exists()) {
      // collision, what should we do?
      if (runtimeConfigs.get(configFile) == Some(runtimeConfig)) {
        // known and same, so silently ignore
        log.debug("RuntimeConfig already persistent")
      } else {
        val msg = "Cannot persist runtime config. Storage location already taken for a different configuration."
        log.error("{} Found file {} with config: {}", msg, configFile, runtimeConfigs.get(configFile))
        sys.error(msg)
      }
    }
    ConfigWriter.write(RuntimeConfigCompanion.toConfig(runtimeConfig), configFile, None)
    runtimeConfigs += configFile -> runtimeConfig
  }

  override def findRuntimeConfigs(): List[RuntimeConfig] = {
    if (!initialized) initialize()
    runtimeConfigs.values.toList
  }
}
