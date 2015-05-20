/*
 * Copyright 2014ff,  https://github.com/woq-blended
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

package blended.akka

import akka.actor.{ActorSystem, PoisonPill, Props}
import blended.akka.protocol.BundleActorStarted
import blended.container.context.ContainerIdentifierService
import org.helgoboss.domino.DominoActivator

trait ActorSystemAware
  extends DominoActivator { 

  def manageBundleActor(f : OSGIActorConfig => Props) : Unit = {

    whenServicesPresent[ActorSystem, ContainerIdentifierService] { (system, idSvc) =>
      val bundleSymbolicName = bundleContext.getBundle().getSymbolicName()
      
      val actorConfig = OSGIActorConfig(bundleContext, idSvc)

      val actorRef = system.actorOf(f(actorConfig), bundleSymbolicName)

      system.eventStream.publish(BundleActorStarted(bundleSymbolicName))
      log info s"Bundle actor started [$bundleSymbolicName]."

      postStartBundleActor()

      onStop {
        preStopBundleActor()
        actorRef ! PoisonPill
      }
    }
  }

  def postStartBundleActor() : Unit = {}

  def preStopBundleActor() : Unit = {}
}
