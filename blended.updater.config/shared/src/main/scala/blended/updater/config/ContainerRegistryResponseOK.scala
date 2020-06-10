package blended.updater.config

/**
 * Used as HTTP response for [[ContainerInfo]] updated, returns potentially update actions.
 * @param id
 */
case class ContainerRegistryResponseOK(id : String)
