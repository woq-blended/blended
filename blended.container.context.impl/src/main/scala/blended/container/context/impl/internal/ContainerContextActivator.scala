package blended.container.context.impl.internal

import scala.util.control.NonFatal
import blended.container.context.api.ContainerIdentifierService
import blended.util.logging.Logger
import scala.collection.JavaConverters._
import domino.DominoActivator
import org.slf4j.MDC

class ContainerContextActivator extends DominoActivator {

  private[this] val mdcPrefix : String = "blended"
  private[this] val log = Logger[ContainerContextActivator]

  private def mdcMap(idSvc : ContainerIdentifierService) : Map[String, String] =
    idSvc.properties.map{ case (k,v) => ( s"$mdcPrefix.$k", v) } + ( s"$mdcPrefix.ctUuid" -> idSvc.uuid)

  whenBundleActive {
    try {
      val containerContext = new ContainerContextImpl()
      val idSvc = new ContainerIdentifierServiceImpl(containerContext)

      log.info(s"Container identifier is [${idSvc.uuid}]")
      log.info(s"Profile home directory is [${containerContext.getProfileDirectory()}]")
      log.info(s"Container Context properties are : ${idSvc.properties.mkString("[", ",", "]")}")

      mdcMap(idSvc).foreach{ case (k,v) => MDC.put(k, v)}
      idSvc.providesService[ContainerIdentifierService]
    } catch {
      case NonFatal(e) =>
        log.error(e.getMessage())
        log.debug(e)("")
    }
  }
}

