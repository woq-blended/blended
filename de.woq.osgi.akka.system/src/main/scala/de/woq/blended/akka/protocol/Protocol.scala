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

package de.woq.blended.akka {

  import akka.actor.ActorRef
  import org.osgi.framework.{BundleContext, ServiceReference}
  import com.typesafe.config.Config

  package object protocol {
    type InvocationType[I <: AnyRef, T <: AnyRef] = (I => T)
  }

  package protocol {

    // Kick off the BundleInitialization
    case class InitializeBundle(context: BundleContext)

    // A bundle has been started via ActorSystemAware
    case class BundleActorStarted(bundleId: String)
    // This can be posted on the Event bus if the bund actor has finished initializing
    case class BundleActorInitialized(bundleId: String)

    // look up a bundleActor by the bundle symbolicName
    case class GetBundleActor(bundleId : String)
    case class BundleActor(bundleId : String, bundleActor : ActorRef)

    // Triger the creation of an Actor for an OSGI Service Reference
    case class CreateReference[I <: AnyRef](clazz : Class[I])

    // This encapsulates a request to retrieve a service reference from the Bundle Context
    case class GetService[I <: AnyRef](clazz : Class[I])

    // This encapsulates a OSGI Reference wrapped in an Actor
    case class Service(service: ActorRef)

    // This is sent to a Service Reference in order to invloke the underlying service
    case class InvokeService[I <: AnyRef,T <: AnyRef](f : I => T)

    // Response for the Service Invocation
    case class ServiceResult[T <: AnyRef](result : Option[T])

    // Release
    case object UngetServiceReference

    // Notifiactions from a ServiceTracker
    case class TrackerAddingService[I <: AnyRef](svcRef : ServiceReference[I], service : I)
    case class TrackerModifiedService[I <: AnyRef](svcRef : ServiceReference[I], service : I)
    case class TrackerRemovedService[I <: AnyRef](svcRef : ServiceReference[I], service : I)

    // Message used to close a tracker
    case object TrackerClose

    //
    // Request - Reply for communicating with the config API
    //
    case class ConfigLocatorRequest(bundleId: String)
    case class ConfigLocatorResponse(bundleId: String, config: Config)

    //
    // Protocol for the EvenSource trait
    //
    case class RegisterListener(listener: ActorRef)
    case class DeregisterListener(listener: ActorRef)
    case class SendEvent[T](event : T)
  }
}
