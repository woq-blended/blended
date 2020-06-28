package blended.updater.config

import java.io.File

import com.typesafe.config.{Config, ConfigFactory}
import scala.util.Try

object ResolvedProfileCompanion {

  def fromConfig(config: Config, featureDir : File): Try[ResolvedProfile] = Try {
    ResolvedProfile(
      profile = ProfileCompanion.read(config.getObject("profile").toConfig()).get,
      featureDir = featureDir
    )
  }

  def toConfig(resolvedProfile: ResolvedProfile): Config = {
    val profileCfg : Config = ProfileCompanion.toConfig(resolvedProfile.profile)
    ConfigFactory.empty().withValue("profile", profileCfg.root())
  }

}
