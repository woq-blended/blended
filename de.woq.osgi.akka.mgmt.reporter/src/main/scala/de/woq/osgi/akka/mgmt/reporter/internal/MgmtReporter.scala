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

import akka.actor.{Actor, ActorLogging, ActorRef, Cancellable}
import de.woq.osgi.akka.system.{OSGIProtocol, OSGIActor, InitializeBundle, BundleName}
import de.woq.osgi.java.container.id.ContainerIdentifierService
import de.woq.osgi.java.mgmt_core.ContainerInfo
import org.osgi.framework.BundleContext
import akka.event.LoggingReceive
import akka.pattern.{ask, pipe}
import scala.util.{Failure, Success}
import scala.concurrent.duration._
import de.woq.osgi.akka.system.OSGIProtocol.{ServiceResult, InvokeService}
import de.woq.osgi.akka.system.internal.OSGIServiceReference
import scala.collection.JavaConversions._

object MgmtReporter {
  def apply()(implicit bundleContext: BundleContext) = new MgmtReporter with OSGIActor with MgmtReporterBundleName
}

class MgmtReporter extends Actor with ActorLogging { this : OSGIActor with BundleName =>

  case object Tick

  private [MgmtReporter] var ticker : Option[Cancellable] = None

  def initializing = LoggingReceive {
    case InitializeBundle(bundleContext) => {
      log info "Initializing Management Reporter"
      ticker = Some(context.system.scheduler.schedule(100.milliseconds, 1.seconds, self, Tick))
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

    case ServiceResult(Some(info))  => log info s"$info"
  }

  def receive = initializing

  override def postStop() {
    ticker match {
      case Some(t) => t.cancel()
      case _ =>
    }
  }

}
