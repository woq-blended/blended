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

package de.woq.blended.itestsupport

import de.woq.blended.itestsupport.condition.Condition
import scala.concurrent.duration._

package object protocol {

  case object ResetPortRange
  case object GetPort
  case class FreePort(p: Int)

  case object ConditionTimeOut
  case object ConditionTick
  case class  ConditionCheckResult(condition: Condition, satisfied: Boolean)

  case object  CheckCondition
  case class ConditionTimeOut(conditions : List[Condition])
  case class ConditionSatisfied(conditions: List[Condition])
}
