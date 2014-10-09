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

  def receive = LoggingReceive {
    case ConditionTick => sender ! ConditionCheckResult(condition, condition.satisfied)
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

  def initializing : Receive = LoggingReceive {
    case CheckCondition => {

      val checker = context.actorOf(checkerProps)

      context become checking(
        sender, checker,
        context.system.scheduler.scheduleOnce(cond.timeout, self, ConditionTimeOut),
        context.system.scheduler.schedule(1.micro, cond.interval, self, ConditionTick)
      )
    }
  }

  def checking(
    checkingFor    : ActorRef,
    checker        : ActorRef,
    checkTimer     : Cancellable,
    timeoutChecker : Cancellable
  ) : Receive = LoggingReceive {
    case ConditionTick => {
      implicit val t = new Timeout(cond.timeout)
      log debug s"Checking Condition [${cond}]."
      ( checker ? ConditionTick ).mapTo[ConditionCheckResult].pipeTo(self)
    }
    case ConditionCheckResult(condition, satisfied)  => {
      if (satisfied) {
        log info s"Condition [${cond}] is now satisfied."
        checkTimer.cancel()
        timeoutChecker.cancel()
        checkingFor ! new ConditionSatisfied(List(condition))
        context.stop(self)
      }
    }
    case ConditionTimeOut => {
      log info s"Condition [${cond}] has timed out."
      checkTimer.cancel()
      timeoutChecker.cancel()
      checkingFor ! new ConditionTimeOut(List(cond))
      context.stop(self)
    }
  }

  override def toString = s"ConditionChecker[${cond}]"
}

