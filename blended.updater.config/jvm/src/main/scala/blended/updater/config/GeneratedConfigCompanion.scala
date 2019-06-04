package blended.updater.config

import com.typesafe.config.{Config, ConfigFactory, ConfigRenderOptions}

object GeneratedConfigCompanion {

  def create(filename : String, cfg : Config) : GeneratedConfig = {

    val json = cfg.root().render(ConfigRenderOptions.concise())
    GeneratedConfig(filename, json)
  }

  def config(cfg : GeneratedConfig) : Config = ConfigFactory.parseString(cfg.config)

}
