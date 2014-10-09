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

import javax.jms.{Connection, ConnectionFactory}

import akka.actor.{Props, ActorLogging, Actor}
import akka.event.LoggingReceive
import de.woq.blended.itestsupport.jms.protocol._

object JMSConnectorActor {
  def apply(cf: ConnectionFactory) = new JMSConnectorActor(cf)
}

class JMSConnectorActor(cf: ConnectionFactory) extends Actor with ActorLogging {

  def receive = disconnected

  def disconnected : Receive = LoggingReceive {
    case Connect(clientId, user, pwd) => {
      val connection = if (user.isDefined && pwd.isDefined)
        cf.createConnection(user.get, pwd.get)
      else
        cf.createConnection()

      connection.setClientID(clientId)
      connection.start()
      context.become(connected(connection))
      sender ! Connected(clientId)
    }
  }

  def connected(connection: Connection) : Receive = LoggingReceive {
    case Disconnect => {
      connection.close()
      context.become(disconnected)
      sender ! Disconnected
    }
    case CreateProducer(destName, msgCounter) => {
      val producer = context.actorOf(Props(Producer(connection, destName, msgCounter)))
      sender ! ProducerActor(producer)
    }
    case CreateConsumer(destName, msgCounter) => {
      val consumer = context.actorOf(Props(Consumer(connection, destName, None, msgCounter)))
      sender ! ConsumerActor(consumer)
    }
    case CreateDurableSubscriber(destName,subscriberName,msgCounter) => {
      val consumer = context.actorOf(Props(Consumer(connection, destName, Some(subscriberName), msgCounter)))
      sender ! ConsumerActor(consumer)
    }
  }
}
