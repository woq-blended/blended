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
import de.woq.blended.itestsupport.protocol._
import de.woq.blended.testsupport.TestActorSys
import org.scalatest.{Matchers, WordSpecLike}
import ConditionProvider._

class ParallelCheckerSpec extends TestActorSys
  with WordSpecLike
  with Matchers {

  "The Condition Checker" should {

    "respond with a satisfied message on an empty list of conditions" in {
      val checker = TestActorRef(Props(ParallelChecker(List.empty)))
      checker ! CheckCondition
      expectMsg(ConditionSatisfied(List.empty))
    }

    "respond with a satisfied message after a single wrapped condition has been satisfied" in {
      val conditions = (1 to 1).map { i => alwaysTrue() }.toList

      val checker = TestActorRef(Props(ParallelChecker(conditions)))
      checker ! CheckCondition

      expectMsg(ConditionSatisfied(conditions))
    }

    "respond with a satisfied message after some wrapped conditions have been satisfied" in {
      val conditions = (1 to 5).map { i => alwaysTrue() }.toList

      val checker = TestActorRef(Props(ParallelChecker(conditions)))
      checker ! CheckCondition

      expectMsg(ConditionSatisfied(conditions))
    }

    "respond with a timeout message after a single wrapped condition has timed out" in {
      val conditions = (1 to 1).map { i => neverTrue() }.toList

      val checker = TestActorRef(Props(SequentialChecker(conditions)))
      checker ! CheckCondition

      expectMsg(ConditionTimeOut(conditions))
    }

    "respond with a timeout message containing the timed out conditions only" in {

      val failCondition   = neverTrue()
      val conditions = List(alwaysTrue(), alwaysTrue(), failCondition, alwaysTrue(), alwaysTrue())

      val checker = TestActorRef(Props(ParallelChecker(conditions)))
      checker ! CheckCondition

      expectMsg(ConditionTimeOut(List(failCondition)))
    }
  }

}
