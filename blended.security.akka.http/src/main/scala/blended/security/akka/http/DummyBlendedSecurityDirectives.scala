package blended.security.akka.http

import akka.http.scaladsl.server.Directive0
import akka.http.scaladsl.server.directives.AuthenticationDirective
import akka.http.scaladsl.server.directives.BasicDirectives.pass
import akka.http.scaladsl.server.directives.BasicDirectives.provide
import javax.security.auth.Subject

trait DummyBlendedSecurityDirectives extends BlendedSecurityDirectives {

  override def authenticated: AuthenticationDirective[Subject] = provide[Subject](null)

  override def requirePermission(permission: String): Directive0 = pass

  override def requireGroup(group: String): Directive0 = pass
}
