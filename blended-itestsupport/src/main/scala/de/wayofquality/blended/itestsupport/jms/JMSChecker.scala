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

package de.wayofquality.blended.itestsupport.jms

import javax.jms.ConnectionFactory

import akka.actor._
import akka.pattern.ask
import akka.util.Timeout
import de.wayofquality.blended.itestsupport.condition.{AsyncChecker, AsyncCondition}
import de.wayofquality.blended.itestsupport.jms.protocol._

import scala.concurrent.duration._

import scala.concurrent.Future

object JMSAvailableCondition{
  def apply(cf: ConnectionFactory, t: Option[FiniteDuration] = None)(implicit system: ActorSystem) =
    AsyncCondition(Props(JMSChecker(cf)), s"JMSAvailableCondition(${cf})", t)
}

private[jms] object JMSChecker {
  def apply(cf: ConnectionFactory) = new JMSChecker(cf)
}

private[jms]class JMSChecker(cf: ConnectionFactory) extends AsyncChecker {

  override def supervisorStrategy = OneForOneStrategy() {
    case _ => SupervisorStrategy.Stop
  }

  var jmsConnector: Option[ActorRef] = None

  override def preStart() : Unit = {
    jmsConnector = Some(context.actorOf(Props(JMSConnectorActor(cf))))
  }

  override def performCheck(cond: AsyncCondition) : Future[Boolean] = {

    implicit val t = Timeout(10.seconds)
    log.debug("Checking JMS connection...")
    (jmsConnector.get ? Connect("test")).mapTo[Either[JMSCaughtException, Connected]].map { result =>
      result match {
        case Left(e) => {
          log.debug(s"Couldn't connect to JMS [${e.inner.getMessage}]")
          false
        }
        case Right(_) => {
          jmsConnector.get ! Disconnect
          true
        }
      }
    }
  }
}
