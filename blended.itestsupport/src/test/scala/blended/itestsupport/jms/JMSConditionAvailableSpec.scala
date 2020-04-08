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

package blended.itestsupport.jms

import akka.actor.ActorSystem
import akka.testkit.{TestActorRef, TestProbe}
import blended.itestsupport.condition.ConditionActor
import blended.itestsupport.condition.ConditionActor.CheckCondition
import blended.itestsupport.condition.ConditionActor.ConditionCheckResult
import blended.jms.utils.{IdAwareConnectionFactory, SimpleIdAwareConnectionFactory}
import blended.testsupport.TestActorSys
import org.apache.activemq.ActiveMQConnectionFactory
import scala.concurrent.duration._

class JMSConditionAvailableSpec extends AbstractJMSSpec {

  "The JMS Available Condition" should {

    "fail if no connection can be made" in TestActorSys { testkit =>
      implicit val system : ActorSystem = testkit.system
      val probe : TestProbe = TestProbe()

      val cf : IdAwareConnectionFactory = SimpleIdAwareConnectionFactory(
        vendor = "amq",
        provider = "amq",
        cf = new ActiveMQConnectionFactory("vm://foo?create=false"),
        clientId = "test",
        minReconnect = 1.second
      )
      val condition = JMSAvailableCondition(cf)

      val checker = TestActorRef(ConditionActor.props(cond = condition))
      checker.tell(CheckCondition, probe.ref)

      probe.expectMsg(ConditionCheckResult(List.empty, List(condition)))
    }

    "succeed if a connection can be made" in TestActorSys { testkit =>
      implicit val system : ActorSystem = testkit.system
      val probe : TestProbe = TestProbe()

      val cf : IdAwareConnectionFactory = SimpleIdAwareConnectionFactory(
        vendor = "amq",
        provider = "amq",
        cf = new ActiveMQConnectionFactory("vm://blended?create=false"),
        clientId = "test",
        minReconnect = 1.second
      )

      val condition = JMSAvailableCondition(cf)

      val checker = TestActorRef(ConditionActor.props(cond = condition))
      checker.tell(CheckCondition, probe.ref)

      probe.expectMsg(ConditionCheckResult(List(condition), List.empty))
    }
  }
}
