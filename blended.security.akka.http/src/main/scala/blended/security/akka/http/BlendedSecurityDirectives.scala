package blended.security.akka.http

import akka.http.scaladsl.server.Directive0
import akka.http.scaladsl.server.directives.AuthenticationDirective
import javax.security.auth.Subject

trait BlendedSecurityDirectives {

  def authenticated: AuthenticationDirective[Subject]

  def requirePermission(permission: String): Directive0

  def requireGroup(group: String) : Directive0
}
