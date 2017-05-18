package blended.security.spray

import org.apache.shiro.subject.Subject
import blended.spray.BlendedHttpRoute
import spray.routing._

trait DummyBlendedSecuredRoute extends BlendedSecuredRoute { this: BlendedHttpRoute =>

  override protected def requirePermission(permission: String): Directive0 = noop

  override protected def authenticated: Directive1[Subject] = provide(null)

}
