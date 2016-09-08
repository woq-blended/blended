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

import akka.actor.Props
import akka.testkit.{TestProbe, ImplicitSender, TestActorRef}
import blended.itestsupport.condition.ConditionProvider._
import blended.itestsupport.protocol._
import blended.testsupport.TestActorSys
import org.scalatest.{Matchers, WordSpec}

class ConditionActorSpec extends WordSpec
  with Matchers {

  "The Condition Actor" should {

    "respond with a satisfied message once the condition was satisfied" in TestActorSys { testkit =>
      implicit val system = testkit.system
      val probe = TestProbe()

      val c = alwaysTrue
      val checker = TestActorRef(Props(ConditionActor(cond = c)))
      checker.tell(CheckCondition, probe.ref)
      probe.expectMsg(ConditionCheckResult(List(c), List.empty[Condition]))
    }

    "respond with a timeout message if the condition wasn't satisfied in a given timeframe" in TestActorSys { testkit =>
      implicit val system = testkit.system
      val probe = TestProbe()

      val c = neverTrue
      val checker = TestActorRef(Props(ConditionActor(cond = c)))
      checker.tell(CheckCondition, probe.ref)
      probe.expectMsg(ConditionCheckResult(List.empty[Condition],List(c)))
    }

    "respond with a satisfied message if a nested parallel condition is satisfied" in TestActorSys { testkit =>
      implicit val system = testkit.system
      val probe = TestProbe()

      val pc = ParallelComposedCondition(alwaysTrue, alwaysTrue)
      val checker = TestActorRef(Props(ConditionActor(pc)))

      checker.tell(CheckCondition, probe.ref)
      probe.expectMsg(ConditionCheckResult(pc.conditions.toList, List.empty[Condition]))
    }
  }
}
