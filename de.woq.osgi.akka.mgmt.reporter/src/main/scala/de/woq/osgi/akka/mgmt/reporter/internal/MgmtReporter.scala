/*
 * Copyright 2013, WoQ - Way of Quality UG(mbH)
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
import de.woq.osgi.akka.modules.RichBundleContext
import de.woq.osgi.java.container.id.ContainerIdentifierService
import de.woq.osgi.java.mgmt_core.ContainerInfo
import scala.collection.JavaConversions._
import scala.concurrent.duration._
import akka.pattern.pipe

object MgmtReporter {
  def apply(name : String) = new MgmtReporter with BundleName {
    override def bundleSymbolicName = name
  }
}

class MgmtReporter extends Actor with ActorLogging { this : BundleName =>

  case object Tick

  implicit val executionContext = context.dispatcher
  implicit val timeout = Timeout(5.seconds)
  val ticker : Cancellable = context.system.scheduler.schedule(5.seconds, 5.seconds, self, Tick)

  def initializing: Receive = {
    case InitializeBundle(bundleContext) => {
      context.become(working(bundleContext))
    }
  }

  def working(context: RichBundleContext) : Receive = {

    case Tick => {
      (context.findService(classOf[ContainerIdentifierService]) andApply {
        idSvc =>
          new ContainerInfo(idSvc.getUUID, idSvc.getProperties.toMap)
      }).pipeTo(self)
    }

    case Some(info : ContainerInfo) => {
    }
  }

  def receive = initializing

  override def postStop() {
    ticker.cancel()
  }

}
