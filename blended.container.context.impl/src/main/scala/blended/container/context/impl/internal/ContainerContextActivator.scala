package blended.container.context.impl.internal

import blended.container.context.api.ContainerIdentifierService
import blended.util.logging.Logger
import domino.DominoActivator

import scala.util.control.NonFatal

class ContainerContextActivator extends DominoActivator {

  private[this] val log = Logger[ContainerContextActivator]

  whenBundleActive {
    try {
      val containerContext = new ContainerContextImpl()
      val idSvc = new ContainerIdentifierServiceImpl(containerContext)
      log.info(s"Container identifier is [${idSvc.uuid}]")
      log.info(s"Profile home directory is [${containerContext.getProfileDirectory()}]")
      log.info(s"Container Context properties are : ${idSvc.properties.mkString("[", ",", "]")}")

      idSvc.providesService[ContainerIdentifierService]
    } catch {
      case NonFatal(e) =>
        log.error(e.getMessage())
        log.debug(e)("")
    }
  }
}

