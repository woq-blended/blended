package de.wayofquality.blended.container.context.internal

import org.helgoboss.domino.DominoActivator
import de.wayofquality.blended.container.context.ContainerContext
import de.wayofquality.blended.container.context.ContainerIdentifierService

class ContainerContextActivator extends DominoActivator {

  whenBundleActive {
    val containerContext = new ContainerContextImpl()

    val idService = new ContainerIdentifierServiceImpl(bundleContext, containerContext)
    idService.providesService[ContainerIdentifierService]
  }

}