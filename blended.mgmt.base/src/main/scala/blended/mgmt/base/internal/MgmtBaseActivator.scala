package blended.mgmt.base.internal

import blended.container.context.api.ContainerIdentifierService
import blended.util.logging.Logger
import domino.DominoActivator
import javax.management.{ MBeanServer, ObjectName }

class MgmtBaseActivator extends DominoActivator {

  private[this] val log = Logger[MgmtBaseActivator]

  whenBundleActive {
    whenServicePresent[ContainerIdentifierService] { idSvc =>

      log.info("Creating Framework Service instance...")

      val fwSvc = new FrameworkService(bundleContext, idSvc.containerContext)
      fwSvc.providesService[blended.mgmt.base.FrameworkService]

      whenServicePresent[MBeanServer] { server =>
        log.info("Registering Framework Service as MBean...")
        val objName = new ObjectName("blended:type=FrameworkService")
        server.registerMBean(fwSvc, objName)
      }
    }
  }
}
