package blended.updater.config

import com.typesafe.config.{Config, ConfigFactory}

import scala.jdk.CollectionConverters._
import scala.util.Try

object ResolvedProfileCompanion {

  def fromConfig(config: Config): Try[ResolvedProfile] = Try {
    ResolvedProfile(
      profile = ProfileCompanion.read(config.getObject("profile").toConfig()).get
    )
  }

  def toConfig(resolvedRuntimeConfig: ResolvedProfile): Config = {
    val config = Map(
      "profile" -> ProfileCompanion.toConfig(resolvedRuntimeConfig.profile).root().unwrapped()
    ).asJava
    ConfigFactory.parseMap(config)
  }

}
