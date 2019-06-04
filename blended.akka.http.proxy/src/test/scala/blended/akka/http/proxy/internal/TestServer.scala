package blended.akka.http.proxy.internal

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Route
import akka.stream.ActorMaterializer
import blended.util.logging.Logger

import scala.concurrent.Await
import scala.concurrent.duration._

object TestServer {

  private[this] val log = Logger[TestServer.type]

  def withServer(
    port : Int,
    route : Route
  )(
    f : => Unit
  )(
    implicit
    actorSystem : ActorSystem,
    actorMaterializer : ActorMaterializer
  ) : Unit = {

    val serverFut = Http().bindAndHandle(route, "localhost", port)
    val server = Await.result(serverFut, 10.seconds)
    try {
      log.info(s"Started server on localhost:$port")
      f
    } finally {
      log.info(s"Stopping server on localhost:$port")
      Await.result(server.unbind(), 10.seconds)
    }
  }

}
