package blended.samples.spray.helloworld.internal

import blended.spray.BlendedHttpRoute
import spray.http.MediaTypes._
import spray.routing._
import org.apache.shiro.util.ThreadContext
import scala.util.Try
import scala.util.Success
import scala.util.Failure

trait HelloService extends BlendedHttpRoute {

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
          requirePermission("hello:view") {
            complete {
              """
            |<html>
            |<body>Say hello to (secured)
            | <i>spray routing</i>
            | within OSGi.</body>
            |</html>
          """.stripMargin
            }
          }
        }
      }
    }
}

