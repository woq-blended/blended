package blended.updater.remote

import java.io.File

import scala.util.Try

import blended.updater.config.{ConfigWriter, Profile, ProfileCompanion}
import blended.util.logging.Logger
import com.typesafe.config.ConfigFactory

class FileSystemRuntimeConfigPersistor(storageDir: File) extends RuntimeConfigPersistor {

  private[this] val log = Logger[FileSystemRuntimeConfigPersistor]

  private[this] var profiles: Map[File, Profile] = Map()
  private[this] var initialized: Boolean = false

  def fileName(rc: Profile): String = s"${rc.name}-${rc.version}.conf"

  def initialize(): Unit = {
    log.debug(s"About to initialize runtime config persistor for storageDir: ${storageDir}")
    profiles = if (!storageDir.exists()) {
      Map()
    } else {
      val files = Option(storageDir.listFiles()).getOrElse(Array())
      val rcs: Seq[(File, Profile)] = files.toSeq.flatMap { file =>
        val rc = Try {
          ConfigFactory.parseFile(file).resolve()
        }.flatMap { rawConfig =>
          ProfileCompanion.read(rawConfig)
        }
        log.debug(s"Found file: ${file} with: ${rc}")
        rc.toOption.map(rc => file -> rc)
      }
      rcs.filter { case (file, rc) => file.getName() == fileName(rc) }.toMap
    }
    initialized = true
  }

  override def persistRuntimeConfig(runtimeConfig: Profile): Unit = {
    if (!initialized) initialize()
    val configFile = new File(storageDir, fileName(runtimeConfig))
    if (configFile.exists()) {
      // collision, what should we do?
      if (profiles.get(configFile) == Some(runtimeConfig)) {
        // known and same, so silently ignore
        log.debug("RuntimeConfig already persistent")
      } else {
        val msg = "Cannot persist runtime config. Storage location already taken for a different configuration."
        log.error(s"${msg} Found file ${configFile} with config: ${profiles.get(configFile)}")
        sys.error(msg)
      }
    }
    ConfigWriter.write(ProfileCompanion.toConfig(runtimeConfig), configFile, None)
    profiles += configFile -> runtimeConfig
  }

  override def findRuntimeConfigs(): List[Profile] = {
    if (!initialized) initialize()
    profiles.values.toList
  }
}
