package blended.mgmt.base.internal

import javax.management.{MBeanServer, ObjectName}

import blended.container.context.ContainerIdentifierService
import domino.DominoActivator
import org.slf4j.LoggerFactory

class MgmtActivator extends DominoActivator {

  private[this] val log = LoggerFactory.getLogger(classOf[MgmtActivator])

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
