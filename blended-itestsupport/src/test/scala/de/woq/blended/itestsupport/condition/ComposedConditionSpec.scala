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

import akka.actor.Props
import akka.testkit.TestActorRef
import de.woq.blended.testsupport.TestActorSys
import org.scalatest.{Matchers, WordSpecLike}
import scala.concurrent.duration._
import ConditionProvider._

import de.woq.blended.itestsupport.protocol._

class ComposedConditionSpec extends TestActorSys
  with WordSpecLike
  with Matchers {

  "A composed condition" should {

    val timeout = 2.seconds

    "be satisfied with an empty condition list" in {
      val condition = new ParallelComposedCondition()(system)

      val checker = TestActorRef(Props(ConditionChecker(cond = condition)))
      checker ! CheckCondition
      expectMsg(ConditionSatisfied(condition :: Nil))
    }

    "be satisfied with a list of conditions that eventually satisfy" in {
      val condition = new ParallelComposedCondition(
        alwaysTrue, alwaysTrue, alwaysTrue, alwaysTrue
      )(system)

      val checker = TestActorRef(Props(ConditionChecker(cond = condition)))
      checker ! CheckCondition
      expectMsg(ConditionSatisfied(condition :: Nil))
    }

    "timeout with at least failing condition" in {
      val condition = new ParallelComposedCondition(
        alwaysTrue, alwaysTrue, neverTrue, alwaysTrue
      )(system)

      val checker = TestActorRef(Props(ConditionChecker(cond = condition)))
      checker ! CheckCondition
      expectMsg(ConditionTimeOut(condition :: Nil))
    }
  }
}
