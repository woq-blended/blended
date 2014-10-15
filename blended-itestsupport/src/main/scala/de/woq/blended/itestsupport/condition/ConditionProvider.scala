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

import java.util.concurrent.atomic.AtomicInteger

import scala.concurrent.duration.FiniteDuration

class AlwaysTrue extends Condition {

  val id = ConditionProvider.counter.incrementAndGet().toString

  override def satisfied(): Boolean = true
  override def toString: String = s"AlwaysTrueCondition[$id]"
}

class NeverTrue extends Condition {
  val id = ConditionProvider.counter.incrementAndGet().toString
  override def satisfied(): Boolean = false
  override def toString: String = s"NeverTrueCondition[$id]"
}

class DelayedTrue(d: FiniteDuration) extends Condition {

  private val id = ConditionProvider.counter.incrementAndGet().toString
  private val created = System.currentTimeMillis()

  override def satisfied(): Boolean = (System.currentTimeMillis() - created) >= d.toMillis
  override def toString: String = s"DelayedTrue[${id}]"
}

object ConditionProvider {
  val counter = new AtomicInteger()
  def alwaysTrue() = new AlwaysTrue
  def neverTrue() = new NeverTrue
}
