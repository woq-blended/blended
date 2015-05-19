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

package blended.itestsupport.jms

import javax.jms._

import akka.actor._
import akka.event.LoggingReceive
import blended.util.protocol.IncrementCounter
import blended.itestsupport.jms.protocol._

object Producer {

  def apply(connection: Connection, destName: String, msgCounter: Option[ActorRef]) =
    new Producer(connection, destName, msgCounter)
}

class Producer(connection: Connection, destName: String, msgCounter: Option[ActorRef])
  extends JMSSupport with Actor with ActorLogging {

  override def jmsConnection = connection

  override def receive = LoggingReceive {

    case produce : ProduceMessage => {
      withSession { session =>
        log.debug(s"Sending [${produce.count}] message(s) to [${destName}]")
        val dest = destination(session, destName)
        val producer = session.createProducer(null)
        val msg = produce.msgFactory.createMessage(session, produce.content)
        for(i <- 1 to produce.count) {
          producer.send(
            dest,
            msg,
            produce.deliveryMode,
            produce.priority,
            produce.ttl
          )
          msgCounter.foreach { counter => counter ! new IncrementCounter() }
        }
      }
      sender ! MessageProduced
    }
  }
}

