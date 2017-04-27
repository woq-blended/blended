package blended.samples.spray.helloworld.internal

import blended.spray.BlendedHttpRoute
import spray.http.MediaTypes._
import spray.routing._

trait HelloService extends BlendedHttpRoute {

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

