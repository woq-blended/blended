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

package de.woq.osgi.akka.system.osgi

import akka.actor.ActorRef

object OSGIProtocol {

  type InvocationType[I <: AnyRef, T <: AnyRef] = (I => T)

  // This encapsulates a request to retrieve a service reference from the Bundle Context
  case class GetService(interface : Class[_ <: AnyRef])

  // This encapsulates a OSGI Reference wrapped in an Actor
  case class Service(service: ActorRef)

  case class InvokeService[I <: AnyRef,T <: AnyRef](f : I => T)

  case class ServiceResult[T <: AnyRef](result : Option[T])

  case object UngetServiceReference
}
