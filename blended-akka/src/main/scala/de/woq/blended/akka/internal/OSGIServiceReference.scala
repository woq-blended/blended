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

package de.woq.blended.akka.internal

import akka.actor.{ActorLogging, Actor}
import org.osgi.framework.ServiceReference
import akka.event.LoggingReceive
import de.woq.blended.akka.protocol._

object OSGIServiceReference {
  def apply[I <: AnyRef](ref : ServiceReference[I]) = new OSGIServiceReference(ref) with BundleContextProvider {
    override val bundleContext = ref.getBundle.getBundleContext
  }
}

class OSGIServiceReference[I <: AnyRef](serviceRef : ServiceReference[I])
  extends Actor with ActorLogging { this : BundleContextProvider =>
  override def receive = LoggingReceive {
    case UngetServiceReference => {
      log debug s"Ungetting Service Reference ${serviceRef.toString}"
      bundleContext.ungetService(serviceRef)
      context.stop(self)
    }

    case InvokeService(f : InvocationType[I, _]) => {
      val result = ( bundleContext.getService(serviceRef) match {
        case null => None
        case svc => Some(f(svc))
      })
      log.debug(s"Service invocation result is [${result}]")
      sender ! ServiceResult(result)
    }
  }
}
