package blended.updater

import java.io.File

import scala.util.Failure
import scala.util.Success
import scala.util.Try

import org.slf4j.LoggerFactory

import com.typesafe.config.ConfigFactory

import blended.updater.config.OverlayConfig
import blended.updater.config.OverlayConfigCompanion

class ProfileFsHelper {
  
  private[this] val log = LoggerFactory.getLogger(classOf[ProfileFsHelper])
  
  def scanForOverlayConfigs(overlayBaseDir: File): List[OverlayConfig] = {
//    val overlayBaseDir = new File(installBaseDir.getParentFile(), "overlays")
    log.debug("Scanning for overlays configs in: {}", overlayBaseDir)

    val confFiles = Option(overlayBaseDir.listFiles).getOrElse(Array()).
      filter(f => f.isFile() && f.getName().endsWith(".conf"))

    val configs = confFiles.toList.flatMap { file =>
      Try {
        ConfigFactory.parseFile(file).resolve()
      }.
        flatMap(OverlayConfigCompanion.read) match {
          case Success(overlayConfig) =>
            List(overlayConfig)
          case Failure(e) =>
            log.error("Could not parse overlay config file: {}", Array(file, e))
            List()
        }
    }

    log.debug("Found overlay configs : {}", configs)

    configs

  }

  
}

object ProfileFsHelper extends ProfileFsHelper