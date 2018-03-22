package blended.domino.internal

import blended.container.context.api.ContainerIdentifierService
import blended.domino.ConfigLocator
import com.typesafe.config.Config
import domino.DominoImplicits
import domino.capsule.{Capsule, CapsuleContext, CapsuleScope}
import domino.service_watching.ServiceWatching
import org.osgi.framework.BundleContext

class TypesafeConfigCapsule(
  cCtxt: CapsuleContext,
  f : (Config, ContainerIdentifierService) => Unit,
  bCtxt : BundleContext) extends Capsule
  with ServiceWatching
  with DominoImplicits {

  override protected def capsuleContext: CapsuleContext = cCtxt
  override protected def bundleContext: BundleContext = bCtxt

  var optCapsuleScope : Option[CapsuleScope] = None

  override def start(): Unit = {
    whenServicePresent[ContainerIdentifierService] { idSvc =>
      val locator = new ConfigLocator(idSvc.containerContext.getProfileConfigDirectory())
      val cfg = locator.getConfig(bundleContext.getBundle().getSymbolicName())

      if (optCapsuleScope.isEmpty) {
        val newCapsuleScope = capsuleContext.executeWithinNewCapsuleScope {
          f(cfg, idSvc)
        }
        optCapsuleScope = Some(newCapsuleScope)
      }
    }
  }

  override def stop(): Unit = {
    optCapsuleScope.foreach(_.stop())
  }
}
