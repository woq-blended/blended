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

package blended.itestsupport

import blended.itestsupport.condition.{AsyncCondition, Condition}
import org.apache.camel.CamelContext

package object protocol {

  // Use this object to query an actor that encapsulates a condition.
  case object CheckCondition

  // Use this object to kick off an Asynchronous checker
  case class CheckAsyncCondition(condition: AsyncCondition)

  // This message collects the results of nested Conditions
  object ConditionCheckResult {
    def apply(results: List[ConditionCheckResult]) = {
      new ConditionCheckResult(
        results.map { r => r.satisfied}.flatten,
        results.map { r => r.timedOut}.flatten
      )
    }
  }
  
  case class ConditionCheckResult(satisfied: List[Condition], timedOut: List[Condition]) {
    def allSatisfied = timedOut.isEmpty

    def reportTimeouts : String =
      timedOut.mkString(
        s"\nA total of [${timedOut.size}] conditions have timed out", "\n", ""
      )
  }
  
  // Use this to kick off the creation of a TestContext based on configured Containers under Test
  case class TestContextRequest(cuts: Map[String, ContainerUnderTest])
  
  // This class returns a TestCamelContext that can be used for the integration tests or an Exception if 
  // the context cannot be created
  case class TestContextResponse(context: Either[Throwable, CamelContext])
  
  case object ContainerReady_?
  case class ContainerReady(ready: Boolean)
  
  case object ConfiguredContainers_?
  case class ConfiguredContainers(cuts : Map[String, ContainerUnderTest])

}
