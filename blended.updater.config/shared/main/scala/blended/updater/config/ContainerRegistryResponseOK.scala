package blended.updater.config

case class ContainerRegistryResponseOK(id: String, actions: List[UpdateAction] = List.empty)
