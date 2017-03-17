package blended.spray

import blended.akka.OSGIActorConfig
import spray.routing.{HttpService, Route}

trait BlendedHttpRoute extends HttpService {

  def actorConfig : OSGIActorConfig
  val httpRoute : Route
}
