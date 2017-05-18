package blended.samples.spray.helloworld.internal

import org.apache.shiro.subject.Subject

import blended.spray.BlendedHttpRoute
import domino.service_consuming.ServiceConsuming
import spray.routing._

trait DummyBlendedSecuredRoute extends BlendedSecuredRoute { this: BlendedHttpRoute =>

  override protected def requirePermission(permission: String): Directive0 = noop

  override protected def authenticated: Directive1[Subject] = provide(null)

}
