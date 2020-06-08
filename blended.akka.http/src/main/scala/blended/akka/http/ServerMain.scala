package blended.akka.http

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.Http.ServerBinding
import akka.http.scaladsl.server.Route
import scala.concurrent.duration._

import scala.concurrent.{Await, ExecutionContext}

trait SimpleWebServer {
  implicit val actorSystem : ActorSystem = ActorSystem("AkkaServer")
  implicit val executionContext : ExecutionContext = actorSystem.dispatcher

  val contentDir : String
  val runFor : FiniteDuration = 1.hour

  private[this] lazy val svrBinding : ServerBinding = {
    val binding = Http().bindAndHandle(route, interface = "localhost", port=0)
    Await.result(binding, 10.seconds)
  }

  private val route : Route = new UiRoute(contentDir, classOf[UiRoute].getClassLoader()).route

  lazy val port : Int = svrBinding.localAddress.getPort

  def run() : Unit = {
    println(s"Started simple akka http server wit port [$port()]")
    Thread.sleep(runFor.toMillis)
  }
}

object ServerMain extends SimpleWebServer {
  override val contentDir: String = "webapp"
  def main(args: Array[String]) : Unit = run()
}
