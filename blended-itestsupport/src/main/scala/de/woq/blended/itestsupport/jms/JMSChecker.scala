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

import javax.jms.ConnectionFactory

import akka.actor._
import akka.pattern.ask
import akka.util.Timeout
import de.woq.blended.itestsupport.condition.{AsyncCondition, AsyncChecker, Condition}
import de.woq.blended.itestsupport.jms.protocol._

import scala.concurrent.Future

object JMSChecker {
  def apply(cf: ConnectionFactory) = new JMSChecker(cf)
}

class JMSChecker(cf: ConnectionFactory) extends AsyncChecker {

  override def supervisorStrategy = OneForOneStrategy() {
    case _ => SupervisorStrategy.Stop
  }

  var jmsConnector: Option[ActorRef] = None

  override def preStart() {
    jmsConnector = Some(context.actorOf(Props(JMSConnectorActor(cf))))
  }

  override def performCheck(cond: AsyncCondition) : Future[Boolean] = {

    implicit val t = Timeout(cond.timeout)
    (jmsConnector.get ? Connect("test")).mapTo[Either[JMSCaughtException, Connected]].map { result =>
      result match {
        case Left(_) => false
        case Right(_) => true
      }
    }
  }
}
