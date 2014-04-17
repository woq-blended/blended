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

import akka.actor.{Cancellable, ActorLogging, Actor}
import de.woq.osgi.akka.system.{InitializeBundle, BundleName}
import akka.util.Timeout
import de.woq.osgi.java.container.id.ContainerIdentifierService
import de.woq.osgi.java.mgmt_core.ContainerInfo
import scala.concurrent.duration._
import org.osgi.framework.BundleContext
import de.woq.osgi.akka.modules._
import scala.collection.JavaConversions._
import akka.event.LoggingReceive

object MgmtReporter {
  def apply(name : String) = new MgmtReporter with BundleName {
    override def bundleSymbolicName = name
  }
}

class MgmtReporter extends Actor with ActorLogging { this : BundleName =>

  case object Tick

  implicit val executionContext = context.dispatcher
  implicit val timeout = Timeout(5.seconds)

  val ticker : Cancellable = 
    context.system.scheduler.schedule(100.milliseconds, 1.seconds, self, Tick)

  def initializing = LoggingReceive {
    case InitializeBundle(bundleContext) => {
      log info "Initializing Management Reporter"
      context.become(working(bundleContext))
    }
  }

  def working(implicit osgiContext: BundleContext) = LoggingReceive {

    case Tick => {
      log info "Performing report"
      (osgiContext.findService(classOf[ContainerIdentifierService])) match {
        case Some(idSvcRef) => idSvcRef invokeService { 
          idSvc => new ContainerInfo(idSvc.getUUID, idSvc.getProperties.toMap) 
        } match {
          case Some(info) => self ! info
          case _ =>
        }
        case _ =>
      }
    }

    case info : ContainerInfo => log info s"$info"
  }

  def receive = initializing

  override def postStop() {
    ticker.cancel()
  }

}
