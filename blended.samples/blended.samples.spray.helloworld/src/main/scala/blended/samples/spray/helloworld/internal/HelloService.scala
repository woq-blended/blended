package blended.samples.spray.helloworld.internal

import org.apache.shiro.subject.Subject

import blended.spray.BlendedHttpRoute
import spray.http.MediaTypes.`text/html`
import spray.routing._

trait HelloService extends BlendedHttpRoute {

  protected def authenticated: Directive1[Subject]

  protected def requirePermission(permission: String): Directive0

  override val httpRoute: Route =
    get {
      respondWithMediaType(`text/html`) {
        path("hello") {
          complete {
            """
            |<html>
            |<body>Say hello to (unsecured)
            | <i>spray routing</i>
            | within OSGi.</body>
            |</html>
          """.stripMargin
          }
        } ~ path("secure" / "hello") {
          authenticated { subject =>
            requirePermission("hello:view") {
              complete {
                s"""
            |<html>
            |<body>Say hello to (secured)
            | <i>spray routing</i>
            | within OSGi. You are ${subject.getPrincipal()}</body>
            |</html>
          """.stripMargin
              }
            }
          }
        }
      }
    }
}

