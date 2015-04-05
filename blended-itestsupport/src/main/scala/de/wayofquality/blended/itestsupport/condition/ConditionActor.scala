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

package de.wayofquality.blended.itestsupport.condition

import akka.actor.{Actor, ActorLogging, ActorRef, Cancellable}
import de.wayofquality.blended.itestsupport.protocol._

object ConditionActor {
  def apply(cond: Condition) = cond match {
    case pc : ParallelComposedCondition => new ParallelConditionActor(pc)
    case sc : SequentialComposedCondition => new SequentialConditionActor(sc)
    case _ => new ConditionActor(cond)
  }
}

class ConditionActor(cond: Condition) extends Actor with ActorLogging {

  case object Tick
  case object Check

  implicit val ctxt = context.system.dispatcher

  def receive = initializing

  def initializing : Receive = {
    case CheckCondition =>
      log.debug(s"Checking condition [${cond.description}] on behalf of [${sender}]")
      val timer = context.system.scheduler.scheduleOnce(cond.timeout, self, Tick)
      context.become(checking(sender, timer))
      self ! Check
  }

  def checking(checkingFor: ActorRef, timer: Cancellable) : Receive = {
    case CheckCondition =>
      log.warning(
        s"""
           |
           |You have sent another CheckCondition message from [${sender}],
           |but this actor is already checking on behalf of [${checkingFor}].
           |
         """)
    case Check => cond.satisfied match {
      case true =>
        log.info(s"Condition [${cond}] is now satisfied.")
        timer.cancel()
        val response = ConditionCheckResult(List(cond), List.empty[Condition])
        log.debug(s"Answering [${response}] to [${checkingFor}]")
        checkingFor ! response
        context.stop(self)
      case false =>
        context.system.scheduler.scheduleOnce(cond.interval, self, Check)
    }
    case Tick  =>
      log.info(s"Condition [${cond}] hast timed out.")
      log.debug(s"Answering to [${checkingFor}]")
      checkingFor ! ConditionCheckResult(List.empty[Condition], List(cond))
      context.stop(self)
  }
}
