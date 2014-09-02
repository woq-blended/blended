package de.woq.blended.akka.itest

import javax.jms.ConnectionFactory

import de.woq.blended.itestsupport.condition.{ParallelComposedCondition, SequentialComposedCondition}
import de.woq.blended.itestsupport.docker.protocol.ContainerManagerStarted
import de.woq.blended.itestsupport.jms.JMSAvailableCondition
import de.woq.blended.itestsupport.jolokia.{JolokiaAvailableCondition, MbeanExistsCondition}
import de.woq.blended.itestsupport.{BlendedIntegrationTestSupport, BlendedTestContext}
import de.woq.blended.jolokia.protocol.MBeanSearchDef
import de.woq.blended.testsupport.TestActorSys
import org.apache.activemq.ActiveMQConnectionFactory
import org.scalatest.{SpecLike, Spec, Suite}

import scala.collection.immutable.IndexedSeq
import scala.concurrent.Await
import scala.concurrent.duration._

class BlendedDemoIntegrationSpec extends TestActorSys
  with SpecLike
  with BlendedIntegrationTestSupport {

  startContainer(30.seconds) should be (ContainerManagerStarted)

  override def nestedSuites = IndexedSeq(new BlendedDemoSpec)

  override def preCondition = {
    val t = 30.seconds

    new SequentialComposedCondition(
      new ParallelComposedCondition(
        new JolokiaAvailableCondition(jmxRest, t, Some("blended"), Some("blended")),
        new JMSAvailableCondition(amqConnectionFactory, t)
      ),
      new MbeanExistsCondition(jmxRest, t, Some("blended"), Some("blended")) with MBeanSearchDef {
        override def jmxDomain = "org.apache.camel"

        override def searchProperties = Map(
          "name" -> "\"BlendedSample\"",
          "type" -> "context"
        )
      }
    )
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
