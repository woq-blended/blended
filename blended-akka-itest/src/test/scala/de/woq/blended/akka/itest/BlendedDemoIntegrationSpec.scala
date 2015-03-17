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

package de.woq.blended.akka.itest

import javax.jms.ConnectionFactory

import de.woq.blended.itestsupport.condition.SequentialComposedCondition
import de.woq.blended.itestsupport.jms.JMSAvailableCondition
import de.woq.blended.itestsupport.jolokia.{CamelContextExistsCondition, JolokiaAvailableCondition}
import de.woq.blended.itestsupport.{BlendedTestContext, BlendedIntegrationTestSupport}
import de.woq.blended.testsupport.TestActorSys
import org.apache.activemq.ActiveMQConnectionFactory
import org.scalatest.SpecLike
import scala.concurrent.duration._

import scala.collection.immutable.IndexedSeq

object BlendedDemoIntegrationSpec {
  val amqConnectionFactory = "amqConnectionFactory"
  val jmxRest = "jmxRest"
}

class BlendedDemoIntegrationSpec extends TestActorSys
  with SpecLike
  with BlendedIntegrationTestSupport {

  override def nestedSuites = IndexedSeq(new BlendedDemoSpec)

//

  private lazy val jmxRest =
    BlendedTestContext.set(BlendedDemoIntegrationSpec.jmxRest, "http://192.168.59.103:49157").asInstanceOf[String]

  private lazy val amqConnectionFactory = BlendedTestContext.set(
      BlendedDemoIntegrationSpec.amqConnectionFactory,
      new ActiveMQConnectionFactory("tcp:192.168.59.103:1883")
    ).asInstanceOf[ConnectionFactory]

}
