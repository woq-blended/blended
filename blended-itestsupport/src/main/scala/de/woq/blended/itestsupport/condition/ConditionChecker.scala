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

package de.woq.blended.itestsupport.condition

import akka.actor._
import akka.event.LoggingReceive
import akka.pattern._
import akka.util.Timeout
import de.woq.blended.itestsupport.protocol._

import scala.concurrent.duration._

class DefaultConditionChecker(condition: Condition) extends Actor with ActorLogging {

  def receive = {
    case CheckCondition => sender ! ConditionCheckResult(condition, condition.satisfied)
  }
}

case object DefaultConditionChecker {
  def apply(condition: Condition) = new DefaultConditionChecker(condition)
}

object ConditionChecker {
  def apply(cond : Condition) =
    new ConditionChecker(cond, Props(DefaultConditionChecker(cond)))

  def apply(condition: Condition, props: Props) = new ConditionChecker(condition, props)
}

class ConditionChecker(
  cond: Condition,
  checkerProps : Props
) extends Actor with ActorLogging {

  implicit val eCtxt = context.dispatcher

  def receive = initializing

  def initializing : Receive = {
    case CheckCondition => {

      val checker = context.actorOf(checkerProps)

      context become busy(sender, checker).orElse(handleTimeout(sender))
      context.system.scheduler.scheduleOnce(cond.timeout, self, ConditionTimeOut)

      checker ! CheckCondition
    }
  }

  def busy(checkingFor: ActorRef, checker: ActorRef) : Receive = {
    case ConditionCheckResult(condition, satisfied)  => {
      if (satisfied) {
        log info s"Condition [${cond}] is now satisfied."
        checkingFor ! new ConditionSatisfied(List(condition))
        context.stop(self)
      } else {
        log.debug(s"Condition [${cond}] is not yet satisfied.")
        context.system.scheduler.scheduleOnce(cond.interval, checker, CheckCondition)
      }
    }
  }

  def handleTimeout(checkingFor: ActorRef) : Receive = {
    case ConditionTimeOut => {
      log info s"Condition [${cond}] has timed out."
      checkingFor ! new ConditionTimeOut(List(cond))
      context.stop(self)
    }
  }

  override def toString = s"ConditionChecker[${cond}]"
}

