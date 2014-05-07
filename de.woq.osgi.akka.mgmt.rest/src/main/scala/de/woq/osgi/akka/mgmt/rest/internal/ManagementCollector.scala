/*
 * Copyright 2014ff, WoQ - Way of Quality UG(mbH)
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

package de.woq.osgi.akka.mgmt.rest.internal

import spray.routing._
import de.woq.osgi.akka.system._
import akka.actor._
import org.osgi.framework.BundleContext
import akka.pattern._
import de.woq.osgi.spray.servlet.{SprayOSGIServlet, SprayOSGIBridge}
import spray.servlet.ConnectorSettings
import akka.event.LoggingReceive
import spray.util.LoggingContext
import de.woq.osgi.akka.system.ConfigLocatorResponse
import de.woq.osgi.akka.system.InitializeBundle
import de.woq.osgi.akka.modules._
import spray.http.Uri.Path
import de.woq.osgi.java.container.registry.{ContainerRegistryResponseOK, UpdateContainerInfo, ContainerInfo}
import scala.concurrent.duration._
import akka.util.Timeout
import spray.httpx.SprayJsonSupport

trait ContainerRegistryProvider {
  def registry : ActorRef
}

trait CollectorService extends HttpService { this : SprayJsonSupport with ContainerRegistryProvider =>

  val collectorRoute = {

    implicit val timeout = Timeout(1.second)
    import scala.concurrent.ExecutionContext.Implicits.global
    import de.woq.osgi.java.container.registry.ContainerRegistryJson._

    path("container") {
      post {
        handleWith { info : ContainerInfo => {
          for { r <- (registry ? UpdateContainerInfo(info)).mapTo[ContainerRegistryResponseOK] } yield r
        }}
      }
    }
  }
}

object ManagementCollector {
  def apply(contextPath: String, reg: ActorRef)(implicit bundleContext: BundleContext) =
    new ManagementCollector(contextPath) with OSGIActor with CollectorBundleName with SprayJsonSupport with ContainerRegistryProvider {
      override def registry = reg
    }

  def apply(contextPath: String, bundleId: String)(implicit bundleContext : BundleContext) =
    new ManagementCollector(contextPath) with OSGIActor with CollectorBundleName with SprayJsonSupport with ContainerRegistryProvider {
      override def registry = context.system.deadLetters
    }
}

class ManagementCollector(contextPath: String)(implicit bundleContext: BundleContext)
  extends CollectorService with Actor with ActorLogging { this : OSGIActor with CollectorBundleName with SprayJsonSupport with ContainerRegistryProvider =>

  override implicit def actorRefFactory = context

  def receive = initializing

  def initializing = LoggingReceive {
    case InitializeBundle(_) => getActorConfig(bundleSymbolicName) pipeTo (self)
    case ConfigLocatorResponse(id, cfg) if id == bundleSymbolicName => {

      implicit val servletSettings = ConnectorSettings(cfg).copy(rootPath = Path(s"/$contextPath"))
      implicit val routingSettings = RoutingSettings(cfg)
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
          "Web-ContextPath" -> s"/$contextPath"
        ))
      context.become(LoggingReceive { runRoute(collectorRoute) })
    }
  }
}
