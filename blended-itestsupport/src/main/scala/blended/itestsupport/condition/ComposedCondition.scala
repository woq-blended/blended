/*
 * Copyright 2014ff,  https://github.com/woq-blended
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

package blended.itestsupport.condition

import java.util.concurrent.atomic.AtomicBoolean

abstract class ComposedCondition(condition: Condition*) extends Condition {

  private var isSatisfied : AtomicBoolean = new AtomicBoolean(false)
  override def satisfied = isSatisfied.get()
}

case class SequentialComposedCondition(conditions: Condition*) extends ComposedCondition(conditions.toSeq:_*) {
  override def timeout = conditions.foldLeft(interval * 2)( (sum, c) => sum + c.timeout)
  override val description = s"SequentialComposedCondition(${conditions.toList}})"
}

case class ParallelComposedCondition(conditions: Condition*) extends ComposedCondition(conditions.toSeq:_*) {
  override def timeout = (conditions.foldLeft(interval * 2)((m, c) => if (c.timeout > m) c.timeout else m)) + interval * 2
  override val description = s"ParallelComposedCondition(${conditions.toList}})"
}
