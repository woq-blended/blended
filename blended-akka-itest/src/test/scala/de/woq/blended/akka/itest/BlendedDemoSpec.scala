package de.woq.blended.akka.itest

import javax.jms.ConnectionFactory

import akka.util.Timeout
import de.woq.blended.itestsupport.BlendedTestContext
import de.woq.blended.itestsupport.camel.TestCamelContext.withTestContext
import de.woq.blended.itestsupport.camel.{CamelTestSupport, TestCamelContext}
import de.woq.blended.testsupport.TestActorSys
import org.apache.camel.component.jms.JmsComponent
import org.scalatest.{DoNotDiscover, Matchers, WordSpecLike}

import scala.concurrent.duration._

@DoNotDiscover
class BlendedDemoSpec extends TestActorSys
  with WordSpecLike
  with Matchers
  with CamelTestSupport {

  implicit val timeOut = new Timeout(3.seconds)
  implicit val eCtxt = system.dispatcher

  val log = system.log

  private def testContext = {
    val result = new TestCamelContext()
    result.withComponent("jms", JmsComponent.jmsComponent(BlendedTestContext("amqConnectionFactory").asInstanceOf[ConnectionFactory]))
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

}
