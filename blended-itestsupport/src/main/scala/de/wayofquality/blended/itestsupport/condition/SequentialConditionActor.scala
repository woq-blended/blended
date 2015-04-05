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

import akka.actor._
import akka.event.LoggingReceive
import de.wayofquality.blended.itestsupport.protocol._

import scala.concurrent.duration.FiniteDuration

object SequentialConditionActor {
  def apply(cond: SequentialComposedCondition) =
    new SequentialConditionActor(cond)
}

class SequentialConditionActor(condition: SequentialComposedCondition) extends Actor with ActorLogging {

  case object SequentialCheck

  var processed : List[Condition] = List.empty
  var remaining : List[Condition] = List.empty

  implicit val eCtxt = context.dispatcher

  def receive = initializing

  def initializing : Receive = {
    case CheckCondition => {
      remaining = condition.conditions.toList
      self ! SequentialCheck
      context become checking(sender)
    }
  }

  def checking(checkingFor : ActorRef ) : Receive = {

    case SequentialCheck => {
      remaining match {
        case Nil  => {
          log.debug(s"Successfully processed [${processed.size}] conditions.")
          checkingFor ! new ConditionCheckResult(processed.reverse, List.empty[Condition])
          context stop self
        }
        case x::xs => {
          remaining = xs
          val subChecker = context.actorOf(Props(ConditionActor(x)))
          subChecker ! CheckCondition
        }
      }
    }
    case cr : ConditionCheckResult => {
      cr.allSatisfied match {
        case true =>
          processed = cr.satisfied.head :: processed
          self ! SequentialCheck
        case _ =>
          remaining = cr.timedOut.head :: remaining
          checkingFor ! ConditionCheckResult(processed.reverse, remaining)
          context.stop(self)
      }
    }
  }

  override def toString = s"SequentialConditionActor(${condition}]"
}
