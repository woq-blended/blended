package blended.container.context.impl.internal

import java.io.{PrintWriter, StringWriter}

import scala.util.control.NonFatal

object IdServiceFactory {

  private[this] val log = org.log4s.getLogger

  def idSvc(ctCtxt : ContainerContext) : ContainerIdentifierService = {
    val result = new ContainerIdentifierServiceImpl(ctCtxt)
    log.info(s"Container identifier is [${result.uuid}]")
    log.info(s"Profile home directory is [${ctCtxt.getProfileDirectory()}]")
    log.info(s"Container Context properties are : ${result.properties.mkString("[", ",", "]")}")

    result
  }
}

class ContainerContextActivator extends DominoActivator {

  private[this] val log = org.log4s.getLogger

  whenBundleActive {
    SLF4JBridgeHandler.removeHandlersForRootLogger()
    SLF4JBridgeHandler.install()

    try {
      val containerContext = new ContainerContextImpl()
      val idSvc = IdServiceFactory.idSvc(containerContext)

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

