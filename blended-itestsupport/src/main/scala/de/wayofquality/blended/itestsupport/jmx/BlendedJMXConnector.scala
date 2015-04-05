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

package de.wayofquality.blended.itestsupport.jmx

import javax.management.remote.{JMXConnectorFactory, JMXConnector}

import akka.actor.{ActorRef, Actor, ActorLogging}
import akka.event.LoggingReceive
import de.wayofquality.blended.itestsupport.jmx.protocol._

trait BlendedJMXConnector extends Actor with ActorLogging{ this : JMXUrlProvider =>

  private var connector : Option[JMXConnector] = None
  private var requests : List[(ActorRef, Any)] = List.empty

  def connected : Receive = LoggingReceive {
    case Disconnect => {
      connector.foreach(_.close())
      connector = None
      sender ! Disconnected
      context become disconnected
    }
  }

  def disconnected : Receive = LoggingReceive {
    case Connect => {
      connector = Some(JMXConnectorFactory.connect(serviceUrl))
      connector.get.connect()
      requests.reverse.foreach{ case (s, m) => self.tell(m, s) }
      sender ! Connected
      context become connected
    }
    case r => requests = (sender, r) :: requests
  }

  def receive = disconnected

  override def postStop() : Unit = {
    connector.foreach(_.close())
  }
}
