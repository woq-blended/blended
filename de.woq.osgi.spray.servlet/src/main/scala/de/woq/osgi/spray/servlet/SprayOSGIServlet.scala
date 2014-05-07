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

package de.woq.osgi.spray.servlet

import akka.actor.{ActorSystem, ActorRef}
import spray.servlet.{Servlet30ConnectorServlet, ConnectorSettings}
import akka.spray.RefUtils
import akka.event.Logging

trait SprayOSGIBridge {
  def actorSystem : ActorSystem
  def connectorSettings : ConnectorSettings
  def routeActor : ActorRef
}

class SprayOSGIServlet extends Servlet30ConnectorServlet { this : SprayOSGIBridge =>

  override def init(): Unit = {
    system = actorSystem
    serviceActor = routeActor
    settings = connectorSettings
    require(system != null, "No ActorSystem configured")
    require(serviceActor != null, "No ServiceActor configured")
    require(settings != null, "No ConnectorSettings configured")
    require(RefUtils.isLocal(serviceActor), "The serviceActor must live in the same JVM as the Servlet30ConnectorServlet")
    timeoutHandler = if (settings.timeoutHandler.isEmpty) serviceActor else system.actorFor(settings.timeoutHandler)
    require(RefUtils.isLocal(timeoutHandler), "The timeoutHandler must live in the same JVM as the Servlet30ConnectorServlet")
    log = Logging(system, this.getClass)
    log.info("Initialized Servlet API 3.0 (OSGi) <=> Spray Connector")
  }
}
