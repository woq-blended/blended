package blended.security.akka.http

import akka.http.scaladsl.server.Directive0
import akka.http.scaladsl.server.directives.AuthenticationDirective
import blended.security.BlendedPermission
import javax.security.auth.Subject

trait BlendedSecurityDirectives {

  val authenticated : AuthenticationDirective[Subject]

  def requirePermission(permission : String) : Directive0 =
    requirePermission(BlendedPermission(Some(permission)))

  def requirePermission(permission : BlendedPermission) : Directive0

  def requireGroup(group : String) : Directive0
}
