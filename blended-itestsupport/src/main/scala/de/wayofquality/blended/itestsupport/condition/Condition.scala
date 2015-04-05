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

import com.typesafe.config.ConfigFactory
import scala.concurrent.duration._

/**
 * A Condition encapsulates an assertion that may change over time. The use case is to
 * wait for a Condition to be satisfied at some - normally that is a pre condition that
 * must be fulfilled before the real tests are executed.
 */
trait Condition {

  /** Is the condition satisfied ? */
  def satisfied   : Boolean
  val description : String

  /** The timeout a ConditionWaiter waits for this particular condition */
  def timeout   : FiniteDuration = defaultTimeout
  def interval  : FiniteDuration = defaultInterval

  lazy val config = {
    val config = ConfigFactory.load()
    config.getConfig("de.wayofquality.blended.itestsupport.condition")
  }

  override def toString = s"Condition($description, $timeout)"

  private[this] def defaultTimeout = config.getLong("defaultTimeout").millis
  private[this] def defaultInterval = config.getLong("checkfrequency").millis
}
