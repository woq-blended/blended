package de.woq.blended.akka.itest

import de.woq.blended.itestsupport.jolokia.JolokiaAvailableCondition
import de.woq.blended.itestsupport.condition.ParallelComposedCondition
import de.woq.blended.itestsupport.jolokia.CamelContextExistsCondition
import de.woq.blended.itestsupport.ContainerUnderTest
import de.woq.blended.itestsupport.jms.JMSAvailableCondition
import de.woq.blended.itestsupport.BlendedTestContextManager
import org.apache.activemq.ActiveMQConnectionFactory
import de.woq.blended.itestsupport.TestContextConfigurator
import de.woq.blended.itestsupport.condition.Condition
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
      JolokiaAvailableCondition(jmxRest(cuts), Some(t), Some("blended"), Some("blended")),
      CamelContextExistsCondition(jmxRest(cuts), Some("blended"), Some("blended"),  "BlendedSample", Some(t))
    )      
  }
}