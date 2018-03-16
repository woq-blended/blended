package blended.security.akka.http

import org.apache.shiro.subject.Subject

import akka.http.scaladsl.server.Directive0
import akka.http.scaladsl.server.directives.AuthenticationDirective

trait BlendedSecurityDirectives {

  def authenticated: AuthenticationDirective[Subject]

  def requirePermission(permission: String): Directive0

}
