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

import de.woq.blended.itestsupport.condition.{ParallelComposedCondition, SequentialComposedCondition}
import de.woq.blended.itestsupport.jms.JMSAvailableCondition
import de.woq.blended.itestsupport.jolokia.{JmsBrokerExistsCondition, CamelContextExistsCondition, JolokiaAvailableCondition, MBeanExistsCondition}
import de.woq.blended.itestsupport.{BlendedIntegrationTestSupport, BlendedTestContext}
import de.woq.blended.jolokia.protocol.MBeanSearchDef
import de.woq.blended.testsupport.TestActorSys
import org.apache.activemq.ActiveMQConnectionFactory
import org.scalatest.{BeforeAndAfterAll, SpecLike}

import scala.collection.immutable.IndexedSeq
import scala.concurrent.Await
import scala.concurrent.duration._

object BlendedDemoIntegrationSpec {
  val amqConnectionFactory = "amqConnectionFactory"
  val jmxRest = "jmxRest"
}

class BlendedDemoIntegrationSpec extends TestActorSys
  with SpecLike
  with BlendedIntegrationTestSupport {

  beforeSuite()

  override def nestedSuites = IndexedSeq(new BlendedDemoSpec)

  override def preCondition = {
    val t = 60.seconds

    SequentialComposedCondition(
      ParallelComposedCondition(
        JolokiaAvailableCondition(jmxRest, Some(t), Some("blended"), Some("blended")),
        JMSAvailableCondition(amqConnectionFactory, Some(t))
      ),
      JmsBrokerExistsCondition(jmxRest, Some("blended"), Some("blended"), "blended", Some(t)),
      CamelContextExistsCondition(jmxRest, Some("blended"), Some("blended"), "BlendedSample", Some(t))
    )
  }

  private lazy val jmxRest = {
    val url = Await.result(jolokiaUrl(ctName = "blended_demo_0", port = 8181), 3.seconds)
    url should not be (None)
    BlendedTestContext.set(BlendedDemoIntegrationSpec.jmxRest, url.get).asInstanceOf[String]
  }

  private lazy val amqConnectionFactory = {
    val ctInfo  = Await.result(containerInfo("blended_demo_0"), 3.seconds)
    val address = ctInfo.getNetworkSettings.getIpAddress

    val brokerUrl = s"tcp://${address}:1883"

    system.log.info(s"Using AMQ connection url [${brokerUrl}]")

    BlendedTestContext.set(
      BlendedDemoIntegrationSpec.amqConnectionFactory,
      new ActiveMQConnectionFactory(brokerUrl)
    ).asInstanceOf[ConnectionFactory]
  }

}
