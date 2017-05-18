package blended.security.spray

import spray.routing._
import org.apache.shiro.subject.Subject

trait BlendedSecuredRoute {

  /**
   * Ensures an authenticated user. The authentication will be established via HTTP BASIC AUTH, so please make sure to only use it over HTTPS.
   */
  protected def authenticated: Directive1[Subject]

  /**
   * Ensures, that the authenticated user has the required permission to proceed.
   */
  protected def requirePermission(permission: String): Directive0

}