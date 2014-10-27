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

import akka.actor.{Cancellable, ActorRef, Actor, ActorLogging}

import de.woq.blended.itestsupport.protocol._

object ConditionActor {
  def apply(cond: Condition) = cond match {
    case pc : ParallelComposedCondition => new ParallelConditionActor(pc.conditions)
    case sc : SequentialComposedCondition => new SequentialConditionActor(sc.conditions)
    case _ => new ConditionActor(cond)
  }
}

class ConditionActor(cond: Condition) extends Actor with ActorLogging {

  case object Tick
  case object Check

  implicit val ctxt = context.system.dispatcher
  var timer : Option[Cancellable] = None

  def receive = initializing

  def initializing : Receive = {
    case CheckCondition =>
      log.debug(s"Checking condition [${cond.description}] on behalf of [${sender}]")
      timer = Some(context.system.scheduler.scheduleOnce(cond.timeout, self, Tick))
      context.become(checking(sender))
      self ! Check
  }

  def checking(checkingFor: ActorRef) : Receive = {
    case Check => cond.satisfied match {
      case true =>
        log.info(s"Condition [${cond}] is now satisfied.")
        timer.foreach(_.cancel())
        log.debug(s"Answering to [${checkingFor}]")
        checkingFor ! ConditionCheckResult(List(cond), List.empty[Condition])
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
