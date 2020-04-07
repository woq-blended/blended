package blended.container.context.impl.internal

import blended.container.context.api.ContainerContext
import blended.util.logging.Logger
import domino.DominoActivator

import scala.util.control.NonFatal

class ContainerContextActivator extends DominoActivator {

  private[this] val mdcPrefix : String = "blended"
  private[this] val log = Logger[ContainerContextActivator]

  private def mdcMap(ctCtxt : ContainerContext) : Map[String, String] =
    ctCtxt.properties.map{ case (k,v) => ( s"$mdcPrefix.$k", v) } + ( s"$mdcPrefix.ctUuid" -> ctCtxt.uuid)

  whenBundleActive {
    try {
      log.info("Initializing Container Context")
      val ctContext = new ContainerContextImpl()
      log.info(s"Started Container Context [$ctContext]")
      Logger.setProps(mdcMap(ctContext))
      ctContext.providesService[ContainerContext]
    } catch {
      case NonFatal(e) =>
        log.error(e.getMessage())
        log.debug(e)("")
    }
  }
}

