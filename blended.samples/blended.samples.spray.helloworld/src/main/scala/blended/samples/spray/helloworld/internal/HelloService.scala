package blended.samples.spray.helloworld.internal

import blended.spray.BlendedHttpRoute
import spray.http.MediaTypes._
import spray.routing._

trait HelloService extends BlendedHttpRoute {

  override val httpRoute: Route = path("hello") {
    get {
      respondWithMediaType(`text/plain`) {
        complete {
          "Say hello to spray routing within OSGi."
        }
      }
    }
  }
}

