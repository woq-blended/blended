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
  def amqUrl(cuts: Map[String, ContainerUnderTest])(implicit dockerHost: String) : String = cuts("blended_node_0").url("jms", dockerHost, "tcp")
  def jmxRest(cuts: Map[String, ContainerUnderTest])(implicit dockerHost: String) : String = s"${cuts("blended_node_0").url("http", dockerHost, "http")}/hawtio/jolokia"
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
        JolokiaAvailableCondition(jmxRest(cuts), Some(t), Some("root"), Some("mysecret"))
      ),
      CamelContextExistsCondition(jmxRest(cuts), Some("root"), Some("mysecret"),  "BlendedSampleContext", Some(t))
    )
  }
}