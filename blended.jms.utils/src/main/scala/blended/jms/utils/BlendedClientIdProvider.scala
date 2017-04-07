package blended.jms.utils

import blended.container.context.ContainerIdentifierService

trait BlendedClientIdProvider {

  def clientId(idSvc: ContainerIdentifierService) : String
}
