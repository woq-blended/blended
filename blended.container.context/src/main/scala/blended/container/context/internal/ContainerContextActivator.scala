package blended.container.context.internal

import blended.container.context.ContainerIdentifierService
import domino.DominoActivator
import org.slf4j.LoggerFactory
import org.slf4j.bridge.SLF4JBridgeHandler

class ContainerContextActivator extends DominoActivator {

  whenBundleActive {
    SLF4JBridgeHandler.removeHandlersForRootLogger()
    SLF4JBridgeHandler.install()

    try {
      val log = LoggerFactory.getLogger(classOf[ContainerContextActivator])
      val containerContext = new ContainerContextImpl()

      val idSvc = new ContainerIdentifierServiceImpl(containerContext)

      log.info("Container identifier is [{}]", idSvc.uuid)
      log.info("Profile home directory is [{}]", containerContext.getContainerDirectory())
      log.info(s"Container Context properties are : ${idSvc.properties.mkString("[", ",", "]")}")

      idSvc.providesService[ContainerIdentifierService]
    }
  }
}