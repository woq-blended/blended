package blended.container.context.impl.internal

import java.io.{PrintWriter, StringWriter}

import blended.container.context.api.ContainerIdentifierService
import domino.DominoActivator
import org.slf4j.bridge.SLF4JBridgeHandler

import scala.util.control.NonFatal

class ContainerContextActivator extends DominoActivator {

  private[this] val log = org.log4s.getLogger

  whenBundleActive {
    SLF4JBridgeHandler.removeHandlersForRootLogger()
    SLF4JBridgeHandler.install()

    try {
      val containerContext = new ContainerContextImpl()
      val idSvc = new ContainerIdentifierServiceImpl(containerContext)
      log.info(s"Container identifier is [${idSvc.uuid}]")
      log.info(s"Profile home directory is [${containerContext.getProfileDirectory()}]")
      log.info(s"Container Context properties are : ${idSvc.properties.mkString("[", ",", "]")}")

      idSvc.providesService[ContainerIdentifierService]
    } catch {
      case NonFatal(e) =>
        if (log.isDebugEnabled) {
          val sw = new StringWriter()
          e.printStackTrace(new PrintWriter(sw))
          log.error(e.getMessage + "\n" + sw.toString)
        } else {
          log.error(e.getMessage())
        }
    }
  }
}

