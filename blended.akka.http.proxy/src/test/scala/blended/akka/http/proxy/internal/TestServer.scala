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

  private var _port : Option[Int] = None
  def port : Int = _port.get

  def withServer(
    route : Route
  )(
    f : Int => Unit
  )(
    implicit
    actorSystem : ActorSystem,
    actorMaterializer : ActorMaterializer
  ) : Unit = {

    val serverFut = Http().bindAndHandle(route, "localhost", 0)
    val server = Await.result(serverFut, 10.seconds)
    _port = Some(server.localAddress.getPort())
    try {
      log.info(s"Started server on localhost:$port")
      f(port)
    } finally {
      log.info(s"Stopping server on localhost:$port")
      Await.result(server.unbind(), 10.seconds)
    }
  }
}
