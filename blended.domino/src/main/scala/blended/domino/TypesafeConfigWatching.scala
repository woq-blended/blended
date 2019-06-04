package blended.domino

import blended.container.context.api.ContainerIdentifierService
import blended.domino.internal.TypesafeConfigCapsule
import com.typesafe.config.Config
import domino.DominoImplicits
import domino.capsule.CapsuleContext
import org.osgi.framework.BundleContext

trait TypesafeConfigWatching extends DominoImplicits {

  /** Dependency */
  protected def capsuleContext : CapsuleContext

  /** Dependency */
  protected def bundleContext : BundleContext

  def whenTypesafeConfigAvailable(f : (Config, ContainerIdentifierService) => Unit) : Unit = {
    val m = new TypesafeConfigCapsule(capsuleContext, f, bundleContext)
    capsuleContext.addCapsule(m)
  }
}
