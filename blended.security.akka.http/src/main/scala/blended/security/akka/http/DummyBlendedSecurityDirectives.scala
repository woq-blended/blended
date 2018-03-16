package blended.security.akka.http

import org.apache.shiro.subject.Subject

import akka.http.scaladsl.server.Directive0
import akka.http.scaladsl.server.directives.AuthenticationDirective
import akka.http.scaladsl.server.directives.BasicDirectives.pass
import akka.http.scaladsl.server.directives.BasicDirectives.provide

class DummyBlendedSecurityDirectives extends BlendedSecurityDirectives {

  override def authenticated: AuthenticationDirective[Subject] = provide[Subject](null)

  override def requirePermission(permission: String): Directive0 = pass

}
