package blended.samples.spray.helloworld.internal

import blended.spray.BlendedHttpRoute
import blended.security.spray.BlendedSecuredRoute
import spray.http.MediaTypes.`text/html`
import spray.routing.Route

trait HelloService
    extends BlendedHttpRoute
    with BlendedSecuredRoute {

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

