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

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.pattern._
import de.wayofquality.blended.itestsupport.protocol._

import scala.concurrent.Future
import scala.language.postfixOps

object ParallelConditionActor {
  def apply(condition: ParallelComposedCondition) =
    new ParallelConditionActor(condition)
}

class ParallelConditionActor(condition: ParallelComposedCondition) extends Actor with ActorLogging {

  case class ParallelCheckerResults(results : List[Any])

  implicit val eCtxt = context.dispatcher

  def receive = initializing

  def initializing : Receive = {
    case CheckCondition => {
      condition.conditions.toSeq match {
        case Nil => sender ! ConditionCheckResult(List.empty[Condition], List.empty[Condition])
        case _ => {
          context become checking(sender)
          // Create a single future that terminates when all condition checkers are done
          // The list will be a mix of ConditionSatisfied / ConditionTimeout messages
          Future.sequence(checker)
            .mapTo[Seq[Any]]
            .map(seq => new ParallelCheckerResults(seq.toList))
            .pipeTo(self)
        }
      }
    }
  }

  def checking(checkingFor: ActorRef) : Receive = {
    case ParallelCheckerResults(results) => {
      log.debug(s"Received ParallelCheckerResults [${results}]")
      val combinedResult = ConditionCheckResult(results.asInstanceOf[List[ConditionCheckResult]])
      log.debug(s"Answering to [${checkingFor}] : [${combinedResult}]")
      checkingFor ! combinedResult
      context stop self
    }
  }

  // Create a list of Future that execute in parallel
  private def checker : Seq[Future[Any]] = condition.conditions.toSeq.map { c =>
    (
      c,                                         // Keep the condition under check in the context
      context.actorOf(Props(ConditionActor(c)))  // The actor checking a single condition
    )
  }.map { p =>
    (p._2 ? CheckCondition)(p._1.timeout).recover {
      case _ => ConditionCheckResult(List.empty[Condition], List(p._1))
    }
  }

  override def toString = s"ParallelConditionActor(${condition})"
}
