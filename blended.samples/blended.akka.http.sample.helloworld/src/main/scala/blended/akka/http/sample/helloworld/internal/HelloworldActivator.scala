package blended.akka.http.sample.helloworld.internal

import domino.DominoActivator
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import blended.akka.http.SimpleHttpContext
import blended.akka.http.HttpContext

class HelloworldActivator extends DominoActivator {

  val helloRoute = get {
    pathEnd {
      complete("Hello World! (with pure route)")
    } ~
      path(Segment) { name =>
        complete(s"Hello $name! (with pure route)")
      }
  }

  val explicitRoute = get {
    pathEnd {
      complete("Hello World! (with explicit context)")
    } ~
      path(Segment) { name =>
        complete(s"Hello $name! (with explicit context)")
      }
  }

  whenBundleActive {

    helloRoute.providesService[Route]("context" -> "helloworld")

    SimpleHttpContext("hello2", explicitRoute).providesService[HttpContext]

  }

}