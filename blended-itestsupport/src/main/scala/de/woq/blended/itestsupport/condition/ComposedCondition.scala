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

import java.util.concurrent.atomic.AtomicBoolean

import akka.actor.{Props, ActorSystem, ActorRef}
import akka.pattern.ask
import de.woq.blended.itestsupport.protocol._

import scala.util.Success

abstract class ComposedCondition(conditions: Condition*)(implicit system: ActorSystem) extends Condition {

  var isSatisfied : AtomicBoolean = new AtomicBoolean(false)

  implicit val eCtxt = system.dispatcher

  (conditionChecker ? CheckCondition)(timeout).onComplete {
    case Success(result) => { result match {
      case ConditionSatisfied(_) => isSatisfied.set(true)
      case _ =>
    }}
    case _ =>
  }

  def conditionChecker : ActorRef
  override def satisfied = isSatisfied.get()
}

class SequentialComposedCondition(conditions: Condition*)
  (implicit system: ActorSystem) extends ComposedCondition {

  override def timeout = conditions.foldLeft(interval * 2)( (sum, c) => sum + c.timeout)
  override def conditionChecker = system.actorOf(Props(SequentialChecker(conditions.toList)))

  override def toString = s"SequentialComposedCondition(${conditions.toList}})"
}

class ParallelComposedCondition(conditions: Condition*)
  (implicit system: ActorSystem) extends ComposedCondition {

  override def timeout = (conditions.foldLeft(interval * 2)((m, c) => if (c.timeout > m) c.timeout else m)) + interval * 2

  override def conditionChecker = system.actorOf(Props(ParallelChecker(conditions.toList)))

  override def toString = s"ParallelComposedCondition(${conditions.toList}})"
}