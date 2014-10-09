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
import ConditionProvider._

import de.woq.blended.itestsupport.protocol._

class ConditionCheckerSpec extends TestActorSys
  with WordSpecLike
  with Matchers {


  "The Condition Checker" should {

    "respond with a satisfied message once the condition was satisfied" in {
      val c = alwaysTrue
      val checker = TestActorRef(Props(ConditionChecker(cond = c)))
      checker ! CheckCondition
      expectMsg(ConditionSatisfied(c :: Nil))
    }

    "respond with a timeout message if the condition wasn't satisfied in a given timeframe" in {
      val c = neverTrue
      val checker = TestActorRef(Props(ConditionChecker(cond = c)))
      checker ! CheckCondition
      expectMsg(ConditionTimeOut(c :: Nil))
    }
  }
}
