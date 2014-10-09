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

package de.woq.blended.itestsupport.condition.jolokia

import akka.actor.Props
import akka.testkit.TestActorRef
import de.woq.blended.itestsupport.condition.{ConditionChecker, ParallelComposedCondition}
import de.woq.blended.itestsupport.jolokia.JolokiaAvailableCondition
import de.woq.blended.itestsupport.protocol.{ConditionSatisfied, CheckCondition}
import de.woq.blended.testsupport.TestActorSys
import org.scalatest.{Matchers, WordSpecLike}
import scala.concurrent.duration._

class JolokiaConditionSpec extends TestActorSys
  with WordSpecLike
  with Matchers {

  "The JolokiaAvailableCondition" should {

    "be satisfied with the intra JVM Jolokia" in {

      val t = 10.seconds

      val condition = new ParallelComposedCondition(
        new JolokiaAvailableCondition(url = "http://localhost:7777/jolokia", timeout = t)
      )(system)

      val checker = TestActorRef(Props(ConditionChecker(cond = condition)))
      checker ! CheckCondition
      expectMsg(t, ConditionSatisfied(condition :: Nil))
    }
  }

}
