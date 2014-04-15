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

package de.woq.osgi.spray.servlet

import akka.actor.{ActorRef, ActorSystem}
import de.woq.osgi.akka.system.{BundleName, ActorSystemAware}

object OSGiConfigHolder {

  private var system : Option[ActorSystem] = None
  private var actor : Option[ActorRef] = None

  def setActorSystem(sys: ActorSystem) {
    this.system = Some(sys)
  }

  def setActorRef(ref : ActorRef) {
    this.actor = Some(ref)
  }


  def actorSystem = system
  def actorRef = actor
}

trait OSGISprayServletActivator extends ActorSystemAware with BundleName {

  override def postStartActor() {
    bundleContext.findService(classOf[ActorSystem]) andApply { sys =>
      OSGiConfigHolder.setActorSystem(sys)
      OSGiConfigHolder.setActorRef(bundleActor)
    }
  }
}
