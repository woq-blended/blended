package blended.akka.http

import domino.DominoActivator

abstract class WebBundleActivator extends DominoActivator {

  val contentDir : String
  val contextName : String

  whenBundleActive {
    val uiRoute = new UiRoute(contentDir, getClass().getClassLoader())
    // In an OSGi container this will be picked by the Akka Http service using a whiteboard pattern
    SimpleHttpContext(contextName, uiRoute.route).providesService[HttpContext]
  }
}


