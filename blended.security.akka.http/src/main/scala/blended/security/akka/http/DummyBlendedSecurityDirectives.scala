package blended.security.akka.http

import akka.http.scaladsl.server.Directive0
import akka.http.scaladsl.server.directives.AuthenticationDirective
import akka.http.scaladsl.server.directives.BasicDirectives.pass
import akka.http.scaladsl.server.directives.BasicDirectives.provide
import blended.security.BlendedPermission
import javax.security.auth.Subject

trait DummyBlendedSecurityDirectives extends BlendedSecurityDirectives {

  override val authenticated: AuthenticationDirective[Subject] = provide[Subject](null)

  override def requirePermission(permission: BlendedPermission): Directive0 = pass

  override def requireGroup(group: String): Directive0 = pass
}
