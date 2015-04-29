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

package de.wayofquality.blended.akka {

  import akka.actor.ActorRef
  import com.typesafe.config.Config
  import org.osgi.framework.BundleContext

  package protocol {
    
    object BundleActorState {
      def apply(config: Config, bundleContext: BundleContext) = new BundleActorState(config, bundleContext)
    }
    
    class BundleActorState(cfg: Config, bc: BundleContext) {
      def config = cfg
      def bundleContext = bc
    }
    
    // Kick off the BundleInitialization
    case class InitializeBundle(context: BundleContext)

    // A bundle has been started via ActorSystemAware
    case class BundleActorStarted(bundleId: String)
    // This can be posted on the Event bus if the bundle actor has finished initializing
    case class BundleActorInitialized(bundleId: String)

    //
    // Protocol for the EvenSource trait
    //
    case class RegisterListener(listener: ActorRef)
    case class DeregisterListener(listener: ActorRef)
    case class SendEvent[T](event : T)
  }
}
