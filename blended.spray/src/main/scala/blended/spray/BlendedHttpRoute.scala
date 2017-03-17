package blended.spray

import blended.akka.OSGIActorConfig
import spray.routing.{HttpService, Route}

trait BlendedHttpRoute extends HttpService {

  // This is the actor configuration that must be made available by the bridge servlet
  // It is required to access any bundle specific configuration in the underlying service
  def actorConfig : OSGIActorConfig

  // This is just a concrete name for the route defined by this service
  val httpRoute : Route
}
