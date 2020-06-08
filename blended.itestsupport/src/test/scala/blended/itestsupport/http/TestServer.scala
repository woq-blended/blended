package blended.itestsupport.http

import scala.concurrent.Await
import scala.concurrent.duration._

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Route
import blended.util.logging.Logger

object TestServer {

  private[this] val log = Logger[TestServer.type]

  def withServer(route: Route)(
    f : Int => Unit
  )(
    implicit actorSystem: ActorSystem,
  ): Unit = {

    val serverFut = Http().bindAndHandle(route, "localhost", 0)
    val server = Await.result(serverFut, 10.seconds)
    try {
      log.info(s"Started test HTTP server on ${server.localAddress}")
      f(server.localAddress.getPort)
    } finally {
      log.info(s"Stopping test HTTP server on ${server.localAddress}")
      Await.result(server.unbind(), 10.seconds)
    }
  }

}
