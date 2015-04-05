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

package de.wayofquality.blended.itestsupport.condition.jolokia

import akka.actor.Props
import akka.testkit.TestActorRef
import de.wayofquality.blended.itestsupport.condition.{Condition, AsyncCondition, ConditionActor}
import de.wayofquality.blended.itestsupport.jolokia.{JolokiaAvailableCondition, JolokiaAvailableChecker}
import de.wayofquality.blended.testsupport.TestActorSys
import org.scalatest.{DoNotDiscover, Matchers, WordSpecLike}

import de.wayofquality.blended.itestsupport.protocol._
import scala.concurrent.duration._

class JolokiaConditionSpec extends TestActorSys
  with WordSpecLike
  with Matchers {

  "The JolokiaAvailableCondition" should {

    "be satisfied with the intra JVM Jolokia" in {

      val t = 10.seconds

      val condition = JolokiaAvailableCondition("http://localhost:7777/jolokia", Some(t))

      val checker = TestActorRef(Props(ConditionActor(cond = condition)))
      checker ! CheckCondition

      expectMsg(t, ConditionCheckResult(List(condition), List.empty[Condition]))
    }

    "fail with a not existing Jolokia" in {

      val t = 10.seconds

      val condition = JolokiaAvailableCondition("http://localhost:8888/jolokia", Some(t))

      val checker = TestActorRef(Props(ConditionActor(cond = condition)))
      checker ! CheckCondition
      expectMsg(t + 1.second, ConditionCheckResult(List.empty[Condition], List(condition)))
    }
  }

}
