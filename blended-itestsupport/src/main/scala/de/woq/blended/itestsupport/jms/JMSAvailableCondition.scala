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

import java.util.concurrent.atomic.AtomicBoolean
import javax.jms.ConnectionFactory

import akka.actor._
import akka.event.LoggingReceive
import akka.pattern.ask
import akka.util.Timeout
import de.woq.blended.itestsupport.camel.CamelTestSupport
import de.woq.blended.itestsupport.condition.{ConditionActor, Condition}
import de.woq.blended.itestsupport.jms.protocol._
import de.woq.blended.itestsupport.protocol._

import scala.concurrent.duration._
import scala.util.Success

class JMSAvailableCondition(
  cf : ConnectionFactory,
  jmsTimeout : FiniteDuration
)(implicit system : ActorSystem) extends Condition {

  implicit val eCtxt = system.dispatcher

  override def timeout = jmsTimeout

  val jmsAvailable = new AtomicBoolean(false)
  val checker      = system.actorOf(Props(ConditionActor(this)))

  (checker ? CheckCondition)(jmsTimeout).onComplete {
    case Success(result) => {
      result match {
      case ConditionSatisfied(_) =>
        jmsAvailable.set(true)
        system.stop(checker)
      case _ =>
    }}
  }

  override def toString = s"jmsAvailableCondition(${cf})"

  override def satisfied = jmsAvailable.get

  object JMSChecker {
    def apply(condition: Condition, cf: ConnectionFactory) = new JMSChecker(condition, cf) with CamelTestSupport
  }

  class JMSChecker(condition: Condition, cf: ConnectionFactory) extends Actor with ActorLogging {

    case class CheckJMS(condition: Condition)

    override def supervisorStrategy = OneForOneStrategy() {
      case _ => SupervisorStrategy.Stop
    }

    def receive = idle

    def idle : Receive = LoggingReceive {
      case CheckCondition => {
        val worker = context.actorOf(Props(new Actor with ActorLogging {

          var jmsConnector: Option[ActorRef] = None

          override def preStart() {
            jmsConnector = Some(context.actorOf(Props(JMSConnectorActor(cf))))
          }

          override def receive: Receive = {
            case CheckJMS(condition) => {
              implicit val timeout = Timeout(1.second)
              val requestor = sender
              (jmsConnector.get ? Connect("test")).mapTo[Either[JMSCaughtException, Connected]].map { result =>
                //TODO
                result match {
                  case Left(e) => requestor ! ConditionCheckResult(List(condition), List.empty[Condition])
                  case Right(c : Connected) => requestor ! "true"
                }
              }
            }
          }
        }))

        context watch worker
        worker ! CheckJMS(condition)
        context become busy(worker, sender)
      }
    }

    def busy(worker: ActorRef, requestor: ActorRef) : Receive = {
      case msg : ConditionCheckResult => {
        context.unwatch(worker)
        context.stop(worker)
        requestor ! msg
        context become idle
      }
      case Terminated(_) => context.become(idle)
    }
  }

}
