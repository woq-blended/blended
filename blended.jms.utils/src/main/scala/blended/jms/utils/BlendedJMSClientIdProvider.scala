package blended.jms.utils

import blended.container.context.ContainerIdentifierService

trait BlendedJMSClientIdProvider {

  def clientId(idSvc: ContainerIdentifierService) : String
}
