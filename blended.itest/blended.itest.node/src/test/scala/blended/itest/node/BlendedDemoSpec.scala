package blended.itest.node

import java.io.File

import akka.actor.{ActorRef, Props}
import akka.camel.CamelExtension
import akka.testkit.{TestActorRef, TestKit, TestProbe}
import akka.util.Timeout
import blended.itestsupport.BlendedIntegrationTestSupport
import blended.testsupport.camel.MockAssertions._
import blended.testsupport.camel.protocol._
import blended.testsupport.camel.{CamelMockActor, CamelTestSupport}
import blended.util.FileHelper
import org.scalatest.{DoNotDiscover, Matchers, WordSpec}

import scala.concurrent.Await
import scala.concurrent.duration.DurationInt

@DoNotDiscover
class BlendedDemoSpec(ctProxy: ActorRef)(implicit testKit : TestKit) extends WordSpec
  with Matchers
  with BlendedIntegrationTestSupport 
  with CamelTestSupport {

  implicit val system = testKit.system
  implicit val timeOut = Timeout(30.seconds)
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

    "Allow to read and write directories via the docker API" in {

      import blended.testsupport.BlendedTestSupport.projectHome

      val file = new File(s"${projectHome}/blended.itest/blended.itest.node/target/test-classes/data")
      val rc = Await.result(writeContainerDirectory(ctProxy, "blended_node_0", "/opt/node", file), timeOut.duration)

      rc should be (Right(true))

      val dir = Await.result(readContainerDirectory(ctProxy, "blended_node_0", "/opt/node/data"), timeOut.duration)

      val fContent = FileHelper.readFile("data/testFile.txt")

      dir.content.get("data/testFile.txt") match {
        case None => fail("expected file [/opt/node/data/testFile.txt] not found in container")
        case Some(c) => c should equal (fContent)
      }
    }
  }
}