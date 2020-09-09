package blended.updater.config

/**
 * Definition of a config file generator.
 * The generator has a file name (relative to the profile) and will write the given config into the config file.
 *
 * @param configFile The relative config file name.
 */
case class GeneratedConfig(configFile : String, config : String)
