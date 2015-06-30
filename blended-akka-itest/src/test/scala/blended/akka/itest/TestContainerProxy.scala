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
import blended.itestsupport.condition.{Condition, ParallelComposedCondition}
import blended.itestsupport.jms.JMSAvailableCondition
import blended.itestsupport.jolokia.{CamelContextExistsCondition, JolokiaAvailableCondition}
import org.apache.activemq.ActiveMQConnectionFactory
import org.apache.camel.CamelContext
import org.apache.camel.component.jms.JmsComponent

import scala.concurrent.duration._

class TestContainerProxy extends BlendedTestContextManager with TestContextConfigurator {
  
  val dockerHost = context.system.settings.config.getString("docker.host")

  private[this] def amqUrl(cuts: Map[String, ContainerUnderTest]) : String = cuts("blended_demo_0").url("jms", dockerHost, "tcp")
  private[this] def jmxRest(cuts: Map[String, ContainerUnderTest]) : String = s"${cuts("blended_demo_0").url("http", dockerHost, "http")}/hawtio/jolokia"
  
  override def configure(cuts: Map[String, ContainerUnderTest], camelCtxt : CamelContext): CamelContext = {
    camelCtxt.addComponent("jms", JmsComponent.jmsComponent(new ActiveMQConnectionFactory(amqUrl(cuts))))
    camelCtxt
  }
  
  override def containerReady(cuts: Map[String, ContainerUnderTest]) : Condition = {
    
    implicit val system = context.system
    val t = 60.seconds
    
    ParallelComposedCondition(
      JMSAvailableCondition(new ActiveMQConnectionFactory(amqUrl(cuts)), Some(t)),
      JolokiaAvailableCondition(jmxRest(cuts), Some(t), Some("blended"), Some("blended"))
      //CamelContextExistsCondition(jmxRest(cuts), Some("blended"), Some("blended"),  "BlendedSample", Some(t))
    )      
  }
}