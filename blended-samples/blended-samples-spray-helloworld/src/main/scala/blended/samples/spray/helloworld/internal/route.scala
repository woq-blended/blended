/*
 * Copyright 2014ff,  https://github.com/woq-blended
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package blended.samples.spray.helloworld.internal

import javax.servlet.Servlet

import akka.actor.{ActorRefFactory, Actor}
import blended.akka.{OSGIActor, OSGIActorConfig}
import blended.spray.{SprayOSGIBridge, SprayOSGIServlet}
import spray.http.MediaTypes._
import spray.http.Uri.Path
import spray.routing._
import spray.servlet.ConnectorSettings
import spray.util.LoggingContext

trait HelloService extends HttpService {

  val helloRoute = path("hello") {
    get {
      respondWithMediaType(`text/html`) {
        complete {
          <html>
            <body>Say hello to <i>spray routing</i> within OSGi.</body>
          </html>
        }
      }
    }
  }
}

object HelloRoute {
  def apply(cfg: OSGIActorConfig, contextPath: String) =
    new HelloRoute(cfg, contextPath)
}

class HelloRoute(cfg: OSGIActorConfig, contextPath: String) extends OSGIActor(cfg) with HelloService {
  
  override implicit def actorRefFactory : ActorRefFactory = context

  override def preStart(): Unit = {

    // We need the full config including settings from application.conf here
    val bundleConfig = bundleActorConfig

    implicit val servletSettings = ConnectorSettings(bundleConfig).copy(rootPath = Path(s"/$contextPath"))
    implicit val routingSettings = RoutingSettings(bundleConfig)
    implicit val routeLogger = LoggingContext.fromAdapter(log)
    implicit val exceptionHandler = ExceptionHandler.default
    implicit val rejectionHandler = RejectionHandler.Default

    val actorSys = context.system
    val routingActor = self

    val servlet = new SprayOSGIServlet with SprayOSGIBridge {
      override def routeActor = routingActor
      override def connectorSettings = servletSettings
      override def actorSystem = actorSys
    }

    servlet.providesService[Servlet] ( Map(
      "urlPatterns" -> "/",
      "Webapp-Context" -> contextPath,
      "Web-ContextPath" -> s"/$contextPath"
    ))

    context.become(runRoute(helloRoute))
  }

  def receive : Receive = Actor.emptyBehavior
}
