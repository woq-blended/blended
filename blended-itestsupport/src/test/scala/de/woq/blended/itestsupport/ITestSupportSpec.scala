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

package de.woq.blended.itestsupport

import de.woq.blended.itestsupport.condition.{SequentialCheckerSpec, ParallelCheckerSpec, ConditionActorSpec, ComposedConditionSpec}
import de.woq.blended.itestsupport.condition.jolokia.JolokiaConditionSpec
import de.woq.blended.itestsupport.docker.{DockerContainerSpec, DependentContainerActorSpec, ContainerUnderTestSpec, ContainerManagerSpec}
import de.woq.blended.itestsupport.jms.{JMSConnectorActorSpec, JMSConditionAvailableSpec}
import org.scalatest.SpecLike

import scala.collection.immutable.IndexedSeq

class ITestSupportSpec extends SpecLike {
  
  override def nestedSuites = IndexedSeq(
//    new JolokiaConditionSpec,
//    new ComposedConditionSpec,
//    new ConditionActorSpec,
//    new ParallelCheckerSpec,
//    new SequentialCheckerSpec,
//    new ContainerManagerSpec,
//    new ContainerUnderTestSpec,
//    new DependentContainerActorSpec,
//    new DockerContainerSpec,
//    new JMSConditionAvailableSpec,
//    new JMSConnectorActorSpec
  )
}
