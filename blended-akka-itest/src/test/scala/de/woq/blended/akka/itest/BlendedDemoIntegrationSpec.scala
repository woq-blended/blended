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
import de.woq.blended.itestsupport.TestContextProvider
import de.woq.blended.itestsupport.BlendedTestContextManager
import de.woq.blended.itestsupport.camel.TestCamelContext

import de.woq.blended.itestsupport.ContainerUnderTest
import org.apache.camel.component.jms.JmsComponent

class BlendedDemoIntegrationSpec extends TestActorSys
  with SpecLike
  with BlendedIntegrationTestSupport {
  
  
  override def nestedSuites = IndexedSeq(new BlendedDemoSpec)
}
