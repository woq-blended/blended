package blended.akka.http.proxy.internal

import akka.actor.ActorSystem
import scala.concurrent.Await
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Route
import scala.concurrent.duration._
import akka.stream.ActorMaterializer

object TestServer {

  private[this] val log = org.log4s.getLogger

  def withServer(
    port: Int,
    route: Route
  )(
    f: => Unit
  )(
    implicit
    actorSystem: ActorSystem,
    actorMaterializer: ActorMaterializer
  ): Unit = {

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