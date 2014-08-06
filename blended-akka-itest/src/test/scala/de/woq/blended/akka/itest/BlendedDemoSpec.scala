package de.woq.blended.akka.itest

import akka.util.Timeout
import de.woq.blended.itestsupport.BlendedIntegrationTestSupport
import de.woq.blended.itestsupport.condition.{ParallelComposedCondition, SequentialComposedCondition}
import de.woq.blended.itestsupport.docker.protocol._
import de.woq.blended.itestsupport.jms.JMSAvailableCondition
import de.woq.blended.itestsupport.jolokia.{MbeanExistsCondition, JolokiaAvailableCondition}
import de.woq.blended.jolokia.protocol.MBeanSearchSpec
import de.woq.blended.testsupport.TestActorSys
import org.apache.activemq.ActiveMQConnectionFactory
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import scala.concurrent.Await
import scala.concurrent.duration._

class BlendedDemoSpec extends TestActorSys
  with WordSpecLike
  with Matchers
  with BeforeAndAfterAll
  with BlendedIntegrationTestSupport {

  implicit val timeOut = new Timeout(3.seconds)
  implicit val eCtxt = system.dispatcher

  val log = system.log

  "The demo container" should {

    "expose Jolokia and JMS" in {

      implicit val t = 30.seconds

      val url = Await.result(jolokiaUrl("blended_demo_0"), t)
      val jmsPort = Await.result(containerPort("blended_demo_0", "jms"), 3.seconds)

      jmsPort should not be (None)
      val cf = new ActiveMQConnectionFactory(s"tcp://localhost:${jmsPort.get}")
      url should not be (None)

      assertCondition(
        new SequentialComposedCondition(
          new ParallelComposedCondition(
            new JolokiaAvailableCondition(url.get, t, Some("blended"), Some("blended")),
            new JMSAvailableCondition(cf, t)
          ),
          new MbeanExistsCondition(url.get, t, Some("blended"), Some("blended")) with MBeanSearchSpec {
            override def jmxDomain = "org.apache.camel"

            override def searchProperties = Map(
              "name" -> "\"BlendedSample\"",
              "type" -> "context"
            )
          }
        )
      ) should be (true)
    }
  }

  override protected def beforeAll() {
    startContainer(30.seconds) should be (ContainerManagerStarted)
  }

  override protected def afterAll() {
    stopContainer(30.seconds) should be (ContainerManagerStopped)
  }
}
