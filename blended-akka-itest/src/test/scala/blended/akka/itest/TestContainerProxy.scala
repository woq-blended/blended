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

package blended.akka.itest

import blended.itestsupport.{BlendedTestContextManager, ContainerUnderTest, TestContextConfigurator}
import blended.itestsupport.condition.{SequentialComposedCondition, Condition, ParallelComposedCondition}
import blended.itestsupport.jms.JMSAvailableCondition
import blended.itestsupport.jolokia.{CamelContextExistsCondition, JolokiaAvailableCondition}
import org.apache.activemq.ActiveMQConnectionFactory
import org.apache.camel.CamelContext
import org.apache.camel.component.jms.JmsComponent

import scala.concurrent.duration._

object TestContainerProxy {
  def amqUrl(cuts: Map[String, ContainerUnderTest])(implicit dockerHost: String) : String = cuts("blended_demo_0").url("jms", dockerHost, "tcp")
  def jmxRest(cuts: Map[String, ContainerUnderTest])(implicit dockerHost: String) : String = s"${cuts("blended_demo_0").url("http", dockerHost, "http")}/hawtio/jolokia"
}

class TestContainerProxy extends BlendedTestContextManager with TestContextConfigurator {

  import TestContainerProxy._
  
  implicit val dockerHost = context.system.settings.config.getString("docker.host")

  override def configure(cuts: Map[String, ContainerUnderTest], camelCtxt : CamelContext): CamelContext = {
    camelCtxt.addComponent("jms", JmsComponent.jmsComponent(new ActiveMQConnectionFactory(amqUrl(cuts))))
    camelCtxt
  }
  
  override def containerReady(cuts: Map[String, ContainerUnderTest]) : Condition = {
    
    implicit val system = context.system
    val t = 60.seconds

    SequentialComposedCondition(
      ParallelComposedCondition(
        JMSAvailableCondition(new ActiveMQConnectionFactory(amqUrl(cuts)), Some(t)),
        JolokiaAvailableCondition(jmxRest(cuts), Some(t), None, None)
      )
      //CamelContextExistsCondition(jmxRest(cuts), None, None,  "BlendedSample", Some(t))
    )
  }
}