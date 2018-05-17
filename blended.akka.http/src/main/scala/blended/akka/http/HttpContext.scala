package blended.akka.http

import akka.http.scaladsl.server.Route

// TODO: think about more advanced options like ranking and the possibility to explicitly allow to extend an existing service
trait HttpContext {
  def prefix: String
  def route: Route
}

case class SimpleHttpContext(prefix: String, route: Route) extends HttpContext

