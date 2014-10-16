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

import akka.actor.Props
import de.woq.blended.itestsupport.condition.AsyncCondition
import de.woq.blended.itestsupport.docker.protocol.ContainerManagerStarted
import de.woq.blended.itestsupport.jms.JMSChecker
import de.woq.blended.itestsupport.{BlendedIntegrationTestSupport, BlendedTestContext}
import de.woq.blended.testsupport.TestActorSys
import org.apache.activemq.ActiveMQConnectionFactory
import org.scalatest.SpecLike

import scala.collection.immutable.IndexedSeq
import scala.concurrent.Await
import scala.concurrent.duration._

class BlendedDemoIntegrationSpec extends TestActorSys
  with SpecLike
  with BlendedIntegrationTestSupport {

  startContainer(30.seconds) should be (ContainerManagerStarted)
  assertCondition(preCondition) should be (true)

  override def nestedSuites = IndexedSeq(new BlendedDemoSpec)

  override def preCondition = {
    val t = 30.seconds

    new AsyncCondition(Props(JMSChecker(amqConnectionFactory))) {
      override def timeout = t
    }

//    new SequentialComposedCondition(
//      new ParallelComposedCondition(
//        //new JolokiaAvailableCondition(jmxRest, t, Some("blended"), Some("blended")),
//        new JMSAvailableCondition(amqConnectionFactory, t)
//      )
////      new MbeanExistsCondition(jmxRest, t, Some("blended"), Some("blended")) with MBeanSearchDef {
////        override def jmxDomain = "org.apache.camel"
////
////        override def searchProperties = Map(
////          "name" -> "\"BlendedSample\"",
////          "type" -> "context"
////        )
////      }
//    )
  }

  private lazy val jmxRest = {
    val url = Await.result(jolokiaUrl(ctName = "blended_demo_0", port = 8181), 3.seconds)
    url should not be (None)
    url.get
  }

  lazy val amqConnectionFactory = {
    val ctInfo = Await.result(containerInfo("blended_demo_0"), 3.seconds)
    val address = ctInfo.getNetworkSettings.getIpAddress
    BlendedTestContext.set("amqConnectionFactory", new ActiveMQConnectionFactory(s"tcp://${address}:1883")).asInstanceOf[ConnectionFactory]
  }
}
