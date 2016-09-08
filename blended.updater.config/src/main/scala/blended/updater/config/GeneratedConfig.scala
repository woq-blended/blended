package blended.updater.config

import com.typesafe.config.Config

/**
 * Definition of a config file generator.
 * The generator has a file name (relative to the profile) and will write the given config into the config file.
 *
 * @param configFile The relative config file name.
 * @param config     The config to be written into the config file.
 */
case class GeneratedConfig(configFile: String, config: Config)