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

package de.wayofquality.blended.util

import akka.actor.{Actor, ActorLogging}
import akka.event.LoggingReceive
import de.wayofquality.blended.util.protocol._

class StatsCounter extends Actor with ActorLogging {

  var count = 0
  var firstCount : Option[Long] = None
  var lastCount  : Option[Long] = None

  override def receive = LoggingReceive {
    case IncrementCounter(c) => {
      firstCount match {
        case None => {
          firstCount = Some(System.currentTimeMillis)
          lastCount = firstCount
        }
        case _ => lastCount = Some(System.currentTimeMillis)
      }
      count += c
    }
    case QueryCounter => sender ! new CounterInfo(
      count,
      firstCount,
      lastCount
    )
  }
}
