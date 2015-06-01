package blended.launcher

import java.io.File
import java.io.File
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
import scala.collection.immutable._
import scala.collection.JavaConverters._
import java.io.BufferedOutputStream
import java.io.FileOutputStream
import java.io.PrintStream

class FileBasedLauncherConfigRepository(configFile: File, configPrefix: String) extends LauncherConfigRepository {

  override def getCurrentConfig(): Option[LauncherConfig] = {
    val config = ConfigFactory.parseFile(configFile)
    if (config.hasPath(configPrefix)) {
      Some(LauncherConfig.read(config.getConfig(configPrefix)))
    } else None
  }

  override def updateConfig(launcherConfig: LauncherConfig): Unit = {
    val config = ConfigFactory.parseFile(configFile)
    val updatedConfig = config.withValue(configPrefix, ConfigValueFactory.fromAnyRef(
      LauncherConfig.toConfig(launcherConfig).root().unwrapped()))
    val os = new PrintStream(new BufferedOutputStream(new FileOutputStream(configFile)))
    try {
      os.print(updatedConfig.root.render())
    } finally {
      os.close()
    }
  }

}