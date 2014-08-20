package de.woq.blended.akka.itest

import akka.util.Timeout
import de.woq.blended.itestsupport.BlendedIntegrationTestSupport
import de.woq.blended.itestsupport.camel.{CamelTestSupport, TestCamelContext}
import de.woq.blended.itestsupport.condition.{Condition, ConditionProvider, ParallelComposedCondition, SequentialComposedCondition}
import de.woq.blended.itestsupport.docker.protocol._
import de.woq.blended.itestsupport.jms.JMSAvailableCondition
import de.woq.blended.itestsupport.jolokia.{MbeanExistsCondition, JolokiaAvailableCondition}
import de.woq.blended.jolokia.protocol.MBeanSearchSpec
import de.woq.blended.testsupport.TestActorSys
import org.apache.activemq.ActiveMQConnectionFactory
import org.apache.camel.{CamelContext, Component}
import org.apache.camel.component.jms.JmsComponent
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import scala.concurrent.Await
import scala.concurrent.duration._

import TestCamelContext.withTestContext

class BlendedDemoSpec extends TestActorSys
  with WordSpecLike
  with Matchers
  with BeforeAndAfterAll
  with BlendedIntegrationTestSupport
  with CamelTestSupport {

  implicit val timeOut = new Timeout(3.seconds)
  implicit val eCtxt = system.dispatcher

  val log = system.log

  private def testContext = {
    val result = new TestCamelContext()
    result.withComponent("jms", JmsComponent.jmsComponent(amqConnectionFactory))
    result
  }

  "The demo container" should {

    "Define the sample Camel Route from SampleIn to SampleOut" in {

      implicit val camelContext = testContext

      withTestContext { ctxt =>
        ctxt.withMock("sampleOut", "jms:queue:SampleOut")
        ctxt.start()

        val mock = ctxt.mockEndpoint("sampleOut")
        mock.setExpectedMessageCount(1)
        mock.expectedBodyReceived().constant("Hello Blended!")

        ctxt.sendTestMessage("Hello Blended!", "jms:queue:SampleIn")
        mock.assertIsSatisfied(2000l)
      }

    }
  }

  override def preCondition = {
    val t = 30.seconds

    new SequentialComposedCondition(
      new ParallelComposedCondition(
        new JolokiaAvailableCondition(jmxRest, t, Some("blended"), Some("blended")),
        new JMSAvailableCondition(amqConnectionFactory, t)
      ),
      new MbeanExistsCondition(jmxRest, t, Some("blended"), Some("blended")) with MBeanSearchSpec {
        override def jmxDomain = "org.apache.camel"

        override def searchProperties = Map(
          "name" -> "\"BlendedSample\"",
          "type" -> "context"
        )
      }
    )
  }

  private lazy val jmxRest = {
    val url = Await.result(jolokiaUrl("blended_demo_0"), 3.seconds)
    url should not be (None)
    url.get
  }

  lazy val amqConnectionFactory = {
    val jmsPort = Await.result(containerPort("blended_demo_0", "jms"), 3.seconds)
    jmsPort should not be (None)
    new ActiveMQConnectionFactory(s"tcp://localhost:${jmsPort.get}")
  }

  override protected def beforeAll() {
    startContainer(30.seconds) should be (ContainerManagerStarted)
    assertCondition(preCondition) should be (true)
  }

  override protected def afterAll() {
    stopContainer(30.seconds) should be (ContainerManagerStopped)
  }
}
