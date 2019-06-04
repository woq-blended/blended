package blended.updater.config

/**
 * Used as HTTP response for [[ContainerInfo]] updated, returns potentially update actions.
 * @param id
 * @param actions
 */
case class ContainerRegistryResponseOK(id : String, actions : List[UpdateAction] = List.empty)
