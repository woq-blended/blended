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

package de.woq.blended.itestsupport.jms

import javax.jms.DeliveryMode

import akka.actor.ActorRef

package object protocol {
  case class Connect(clientId: String, user: Option[String] = None, password : Option[String] = None)
  case class Connected(clientId: String)

  case object Disconnect
  case object Disconnected

  case class CreateProducer(destName: String, msgCounter: Option[ActorRef] = None)
  case class CreateConsumer(destName: String, msgCounter: Option[ActorRef] = None)
  case class CreateDurableSubscriber(topic: String, subScriberName: String, msgCounter: Option[ActorRef] = None)

  case object StopConsumer

  case class ProduceMessage(
    msgFactory: JMSMessageFactory,
    content: Option[Any] = None,
    count : Int = 1,
    deliveryMode : Int = DeliveryMode.NON_PERSISTENT,
    priority : Int = 4,
    ttl : Long = 0
  )
  case object MessageProduced

  case class ProducerActor(producer: ActorRef)
  case class ConsumerActor(consumer: ActorRef)

  case object Unsubscribe
  case class ConsumerStopped(destName: String)
}
