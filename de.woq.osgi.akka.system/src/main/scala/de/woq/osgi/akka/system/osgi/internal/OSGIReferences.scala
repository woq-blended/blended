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

package de.woq.osgi.akka.system.osgi.internal

import akka.actor._
import de.woq.osgi.akka.system.osgi.OSGIProtocol
import org.osgi.framework.BundleContext
import de.woq.osgi.akka.modules._
import akka.event.LoggingReceive
import scala.Some
import akka.actor.Props
import akka.actor.SupervisorStrategy.Stop

object OSGIReferences {

  def apply(osgiContext : BundleContext) = new OSGIReferences with BundleContextProvider {
    override def bundleContext = osgiContext
  }
}

class OSGIReferences extends Actor with ActorLogging { this : BundleContextProvider =>

  override def supervisorStrategy = OneForOneStrategy() {
    case _ => Stop
  }

  override def receive = LoggingReceive {
    case OSGIFacade.CreateReference(clazz) => {
      bundleContext findService(clazz) match {
        case Some(ref) => {
          log info s"Creating Service reference actor..."
          sender ! OSGIProtocol.Service(context.actorOf(Props(OSGIServiceReference(ref))))
        }
        case _ =>
      }
    }
  }
}
