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

import de.woq.blended.itestsupport.BlendedIntegrationTestSupport
import de.woq.blended.testsupport.TestActorSys
import org.scalatest.SpecLike

import scala.collection.immutable.IndexedSeq

object BlendedDemoIntegrationSpec {
  val amqConnectionFactory = "amqConnectionFactory"
  val jmxRest = "jmxRest"
}

class BlendedDemoIntegrationSpec extends TestActorSys
  with SpecLike
  with BlendedIntegrationTestSupport {

  override def nestedSuites = IndexedSeq(new BlendedDemoSpec)

//  override def preCondition = {
//    val t = 3600.seconds
//
//    SequentialComposedCondition(
//      JMSAvailableCondition(amqConnectionFactory, Some(t)),
//      JolokiaAvailableCondition(jmxRest, Some(t), Some("blended"), Some("blended")),
//      CamelContextExistsCondition(
//        jmxRest, Some("blended"), Some("blended"), "BlendedSample", Some(t)
//      )
//    )
//  }
//
//  private lazy val jmxRest = {
//    val url = Await.result(jolokiaUrl(ctName = "blended_demo_0", port = 8181), 3.seconds)
//    url should not be None
//    BlendedTestContext.set(BlendedDemoIntegrationSpec.jmxRest, url.get).asInstanceOf[String]
//  }
//
//  private lazy val amqConnectionFactory = {
//    val ctInfo  = Await.result(containerInfo("blended_demo_0"), 3.seconds)
//    val address = ctInfo.getNetworkSettings.getIpAddress
//
//    val brokerUrl = s"tcp://$address:1883"
//
//    system.log.info(s"Using AMQ connection url [$brokerUrl]")
//
//    BlendedTestContext.set(
//      BlendedDemoIntegrationSpec.amqConnectionFactory,
//      new ActiveMQConnectionFactory(brokerUrl)
//    ).asInstanceOf[ConnectionFactory]
//  }

}
