package blended.akka.itest

import akka.actor.Props
import akka.camel.CamelExtension
import akka.testkit.{TestActorRef, TestKit, TestProbe}
import akka.util.Timeout
import blended.itestsupport.BlendedIntegrationTestSupport
import blended.testsupport.camel.MockAssertions._
import blended.testsupport.camel.protocol._
import blended.testsupport.camel.{CamelMockActor, CamelTestSupport}
import org.scalatest.{DoNotDiscover, Matchers, WordSpec}

import scala.concurrent.duration.DurationInt

@DoNotDiscover
class BlendedDemoSpec(implicit testKit : TestKit) extends WordSpec
  with Matchers
  with BlendedIntegrationTestSupport 
  with CamelTestSupport {

  implicit val system = testKit.system
  implicit val timeOut = new Timeout(3.seconds)
  implicit val eCtxt = testKit.system.dispatcher
  override implicit val camelContext = CamelExtension.get(system).context

  private[this] val log = testKit.system.log

  "The demo container" should {

    "Define the sample Camel Route from SampleIn to SampleOut" in {

      val mock = TestActorRef(Props(CamelMockActor("jms:queue:SampleOut")))
      val mockProbe = new TestProbe(system)
      testKit.system.eventStream.subscribe(mockProbe.ref, classOf[MockMessageReceived])
 
      sendTestMessage("Hello Blended!", Map("foo" -> "bar"), "jms:queue:SampleIn", binary = false) match {
        // We have successfully sent the message 
        case Right(msg) =>
          // make sure the message reaches the mock actors before we start assertions
          mockProbe.receiveN(1)
          
          checkAssertions(mock, 
            expectedMessageCount(1), 
            expectedBodies("Hello Blended!"),
            expectedHeaders(Map("foo" -> "bar"))
          ) should be(List.empty) 
        // The message has not been sent
        case Left(e) => 
          log.error(e.getMessage, e)
          fail(e.getMessage)
      }
    }
  }
}