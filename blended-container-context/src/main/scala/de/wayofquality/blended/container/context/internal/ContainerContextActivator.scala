package de.wayofquality.blended.container.context.internal

import org.helgoboss.domino.DominoActivator

import de.wayofquality.blended.container.context.ContainerContext

class ContainerContextActivator extends DominoActivator {

  whenBundleActive {
    val containerContext = new ContainerContextImpl()
    containerContext.providesService[ContainerContext]
  }

}