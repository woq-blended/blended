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

import akka.actor.{Cancellable, Actor, ActorLogging}
import de.woq.blended.itestsupport.protocol.CheckAsyncCondition

import scala.concurrent.Future

abstract class AsyncChecker extends Actor with ActorLogging {

  protected implicit val ctxt = context.system.dispatcher

  case object Tick
  case object Stop

  def performCheck(condition: AsyncCondition) : Future[Boolean]

  def receive = initializing

  def initializing : Receive = {
    case CheckAsyncCondition(condition) =>
      log.debug("Starting asynchronous condition checker")
      self ! Tick
      val timer = context.system.scheduler.scheduleOnce(condition.timeout, self, Stop)
      context.become(checking(condition, timer))
  }

  def checking(condition: AsyncCondition, timer: Cancellable) : Receive = {
    case Tick =>
      log.debug("Checking asynchronous condition ....")
      performCheck(condition).map(_ match {
        case true =>
          log.debug(s"Asynchronous condition is now satisfied.")
          timer.cancel()
          condition.isSatisfied.set(true)
          context.stop(self)
        case false =>
          log.debug(s"Scheduling next condition check in [${condition.interval}]")
          context.system.scheduler.scheduleOnce(condition.interval, self, Tick)
      })
    case Stop =>
      log.info("Asynchronous condition timed out")
      context.stop(self)
  }
}
