/*
 * Copyright 2014ff, WoQ - Way of Quality GmbH
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

package de.wayofquality.blended.samples.spray.helloworld.internal

import akka.actor.{Actor, ActorLogging}
import com.typesafe.config.Config
import de.wayofquality.blended.akka.{InitializingActor, BundleName, OSGIActor}
import de.wayofquality.blended.spray.{SprayOSGIServlet, SprayOSGIBridge}
import spray.http.MediaTypes._
import org.osgi.framework.BundleContext
import spray.servlet.ConnectorSettings
import akka.event.LoggingReceive
import spray.util.LoggingContext
import spray.http.Uri.Path

import spray.routing._
import akka.pattern._

import de.wayofquality.blended.modules._
import de.wayofquality.blended.akka.protocol._

import scala.util.{Success, Try}

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
  def apply(contextPath: String, bc: BundleContext) =
    new HelloRoute(contextPath, bc) with OSGIActor with HelloBundleName
}

class HelloRoute(contextPath: String, bc: BundleContext)
  extends HelloService with InitializingActor[BundleActorState] with ActorLogging with HelloBundleName{
  
  override protected def bundleContext: BundleContext = bc

  override def createState(cfg: Config, bundleContext: BundleContext): BundleActorState = 
    BundleActorState(cfg, bundleContext)

  override implicit def actorRefFactory = context

  override def receive: Actor.Receive = initializing


  override def initialize(state: BundleActorState): Try[Initialized] = {
    implicit val servletSettings = ConnectorSettings(state.config).copy(rootPath = Path(s"/$contextPath"))
    implicit val routingSettings = RoutingSettings(state.config)
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

    bundleContext.createService(
      servlet, Map(
        "urlPatterns" -> "/",
        "Webapp-Context" -> contextPath,
        "Web-ContextPath" -> s"/$contextPath",
        "servlet-name" -> "hello"
      ))
  
    Success(Initialized(state))
  }
  
  override def working(state: BundleActorState): Receive = LoggingReceive { runRoute(helloRoute) }
}
