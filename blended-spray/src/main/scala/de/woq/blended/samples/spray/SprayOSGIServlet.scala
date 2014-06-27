package de.woq.blended.samples.spray

import akka.actor.{ActorRef, ActorSystem}
import akka.event.Logging
import akka.spray.RefUtils
import spray.servlet.{ConnectorSettings, Servlet30ConnectorServlet}

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
