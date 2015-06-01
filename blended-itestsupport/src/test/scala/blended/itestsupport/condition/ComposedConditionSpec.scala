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
import akka.testkit.{TestProbe, TestActorRef}
import blended.itestsupport.condition.ConditionProvider._
import blended.itestsupport.protocol._
import blended.testsupport.TestActorSys
import org.scalatest.{Matchers, WordSpec}

import scala.concurrent.duration._

class ComposedConditionSpec extends WordSpec
  with Matchers {

  "A composed condition" should {

    val timeout = 2.seconds

    "be satisfied with an empty condition list" in TestActorSys { testkit =>
      implicit val system = testkit.system
      val probe = TestProbe()

      val condition = new ParallelComposedCondition()

      val checker = TestActorRef(Props(ConditionActor(cond = condition)))
      checker.tell(CheckCondition, probe.ref)
      probe.expectMsg(ConditionCheckResult(List.empty, List.empty))
    }

    "be satisfied with a list of conditions that eventually satisfy" in TestActorSys { testkit =>
      implicit val system = testkit.system
      val probe = TestProbe()

      val conditions = List(alwaysTrue(), alwaysTrue(), alwaysTrue(), alwaysTrue())
      val condition = new ParallelComposedCondition(conditions.toSeq:_*)

      val checker = TestActorRef(Props(ConditionActor(cond = condition)))
      checker.tell(CheckCondition, probe.ref)
      probe.expectMsg(ConditionCheckResult(conditions, List.empty[Condition]))
    }

    "timeout with at least failing condition" in TestActorSys { testkit =>
      implicit val system = testkit.system
      val probe = TestProbe()

      val conditions = List(alwaysTrue(), alwaysTrue(), neverTrue(), alwaysTrue())
      val condition = new ParallelComposedCondition(conditions.toSeq:_*)

      val checker = TestActorRef(Props(ConditionActor(cond = condition)))
      checker.tell(CheckCondition, probe.ref)

      probe.expectMsg(ConditionCheckResult(
        conditions.filter(_.isInstanceOf[AlwaysTrue]),
        conditions.filter(_.isInstanceOf[NeverTrue])
      ))
    }
  }
}
