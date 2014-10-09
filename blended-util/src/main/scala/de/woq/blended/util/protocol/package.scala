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

package de.woq.blended.util

import java.util.concurrent.TimeUnit

import scala.concurrent.duration._

package object protocol {

  case class  IncrementCounter(val count : Int = 1)
  case object QueryCounter
  case class  CounterInfo(
    count : Int,
    firstCount: Option[Long],
    lastCount: Option[Long]
  ) {

    def interval : Duration =
      if (firstCount.isDefined && lastCount.isDefined)
        (lastCount.get - firstCount.get).millis
      else
        0.millis

    def speed(unit: TimeUnit = TimeUnit.MILLISECONDS) = {
      interval.length match {
        case 0 => if (count == 0) 0.0 else Double.MaxValue
        case _ => count.asInstanceOf[Double] / (interval.length) * unit.toMillis(1)
      }
    }
  }

  case object StopCounter

}
