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

import akka.actor.{ActorSystem, Props}
import de.woq.blended.itestsupport.protocol.CheckAsyncCondition

import scala.concurrent.duration.FiniteDuration

object AsyncCondition{
  def apply(asyncChecker: Props, desc: String, t: Option[FiniteDuration] = None)(implicit system: ActorSystem) = t match {
    case None => new AsyncCondition(asyncChecker, desc)
    case Some(d) => new AsyncCondition(asyncChecker, desc) {
      override def timeout = d
    }
  }
}

class AsyncCondition(asyncChecker: Props, desc: String)(implicit val system: ActorSystem) extends Condition {

  val isSatisfied = new AtomicBoolean(false)
  override def satisfied = isSatisfied.get()

  val checker = system.actorOf(asyncChecker)
  checker ! CheckAsyncCondition(this)

  override val description: String = desc
}
