package blended.container.context.impl.internal

import blended.container.context.api.{ContainerContext, ContainerIdentifierService}
import blended.util.logging.Logger
import domino.DominoActivator

import scala.util.control.NonFatal

class ContainerContextActivator extends DominoActivator {

  private[this] val mdcPrefix : String = "blended"
  private[this] val log = Logger[ContainerContextActivator]

  private def mdcMap(idSvc : ContainerIdentifierService) : Map[String, String] =
    idSvc.properties.map{ case (k,v) => ( s"$mdcPrefix.$k", v) } + ( s"$mdcPrefix.ctUuid" -> idSvc.uuid)

  whenBundleActive {
    try {
      val ctContext = new ContainerContextImpl()

      log.info(s"Container identifier is [${ctContext.identifierService.uuid}]")
      log.info(s"Profile home directory is [${ctContext.profileDirectory}]")
      log.info(s"Container Context properties are : ${ctContext.identifierService.properties.mkString("[", ",", "]")}")

      Logger.setProps(mdcMap(ctContext.identifierService))
      ctContext.providesService[ContainerContext]
    } catch {
      case NonFatal(e) =>
        log.error(e.getMessage())
        log.debug(e)("")
    }
  }
}

