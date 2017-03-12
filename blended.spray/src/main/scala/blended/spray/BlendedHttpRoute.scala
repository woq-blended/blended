package blended.spray

import spray.routing.{HttpService, Route}

trait BlendedHttpRoute extends HttpService {

  val httpRoute : Route
}
