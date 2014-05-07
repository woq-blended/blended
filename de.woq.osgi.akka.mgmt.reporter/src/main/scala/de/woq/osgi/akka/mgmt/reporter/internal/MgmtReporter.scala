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

package de.woq.osgi.akka.mgmt.reporter.internal

import akka.actor.{Actor, ActorLogging, Cancellable}
import de.woq.osgi.akka.system.{OSGIActor, InitializeBundle, BundleName}
import de.woq.osgi.java.container.id.ContainerIdentifierService
import org.osgi.framework.BundleContext
import akka.event.LoggingReceive
import akka.pattern.pipe
import scala.concurrent.duration._
import de.woq.osgi.akka.system.OSGIProtocol.ServiceResult
import scala.collection.JavaConversions._
import de.woq.osgi.java.container.registry.ContainerInfo
import spray.json._
import DefaultJsonProtocol._
import de.woq.osgi.java.container.registry.ContainerInfoJson._

object MgmtReporter {
  def apply()(implicit bundleContext: BundleContext) = new MgmtReporter with OSGIActor with MgmtReporterBundleName
}

class MgmtReporter extends Actor with ActorLogging { this : OSGIActor with BundleName =>

  case object Tick

  private [MgmtReporter] var ticker : Option[Cancellable] = None

  def initializing = LoggingReceive {
    case InitializeBundle(bundleContext) => {
      log info "Initializing Management Reporter"
      ticker = Some(context.system.scheduler.schedule(100.milliseconds, 60.seconds, self, Tick))
      context.become(working(bundleContext))
    }
  }

  def working(implicit osgiContext: BundleContext) = LoggingReceive {

    case Tick => {

      log debug "Performing report"

      invokeService[ContainerIdentifierService, ContainerInfo](classOf[ContainerIdentifierService]) { idSvc =>
        new ContainerInfo(idSvc.getUUID, idSvc.getProperties.toMap)
      } pipeTo(self)
    }

    case ServiceResult(Some(info : ContainerInfo))  => log info s"${info.toJson.compactPrint}"
  }

  def receive = initializing

  override def postStop() {
    ticker match {
      case Some(t) => t.cancel()
      case _ =>
    }
  }
}
