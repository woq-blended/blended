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
import de.woq.blended.itestsupport.protocol._

import scala.concurrent.duration.FiniteDuration

object SequentialChecker {
  def apply(conditions: List[Condition]) = new SequentialChecker(conditions)
}

class SequentialChecker(conditions: List[Condition]) extends Actor with ActorLogging {

  case object SequentialCheck

  var processed : List[Condition] = List.empty
  var remaining : List[Condition] = List.empty

  implicit val eCtxt = context.dispatcher

  def receive = initializing

  def initializing = LoggingReceive {
    case CheckCondition => {
      remaining = conditions
      self ! SequentialCheck
      context become checking(sender)
    }
  }

  def checking(checkingFor : ActorRef ) = LoggingReceive {

    case SequentialCheck => {
      remaining match {
        case Nil  => {
          log.debug(s"Successfully processed [${processed.size}] conditions.")
          checkingFor ! new ConditionSatisfied(processed.reverse)
          context stop self
        }
        case x::xs => {
          remaining = xs
          val subChecker = context.actorOf(Props(ConditionChecker(cond = x)))
          subChecker ! CheckCondition
        }
      }
    }
    case ConditionSatisfied(c :: Nil) => {
      processed = c :: processed
      self ! SequentialCheck
    }
    case ConditionTimeOut(c :: Nil) => {
      remaining = c :: remaining
      checkingFor ! ConditionTimeOut(remaining)
      context stop self
    }
  }
}
