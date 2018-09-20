package blended.updater.config

import com.typesafe.config.{Config, ConfigFactory}

import scala.collection.JavaConverters.mapAsJavaMapConverter
import scala.util.Try

object ResolvedRuntimeConfigCompanion {

  def fromConfig(config: Config): Try[ResolvedRuntimeConfig] = Try {
    ResolvedRuntimeConfig(
      runtimeConfig = RuntimeConfigCompanion.read(config.getObject("runtimeConfig").toConfig()).get
    )
  }

  def toConfig(resolvedRuntimeConfig: ResolvedRuntimeConfig): Config = {
    val config = Map(
      "runtimeConfig" -> RuntimeConfigCompanion.toConfig(resolvedRuntimeConfig.runtimeConfig).root().unwrapped()
    ).asJava
    ConfigFactory.parseMap(config)
  }

}