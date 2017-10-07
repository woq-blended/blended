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

import scala.concurrent.duration.DurationInt
import scala.util.{Failure, Success}

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
      testKit.system.eventStream.subscribe(mockProbe.ref, classOf[ReceiveStopped])

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

          mock.tell(StopReceive, mockProbe.ref)
          mockProbe.receiveN(1)
        // The message has not been sent
        case Left(e) =>
          log.error(e.getMessage, e)
          fail(e.getMessage)
      }

    }

    "Allow to read and write directories via the docker API" in {

      import blended.testsupport.BlendedTestSupport.projectTestOutput

      val file = new File(s"${projectTestOutput}/data")

      writeContainerDirectory(ctProxy, "blended_node_0", "/opt/node", file).onComplete {
        case Failure(t) => fail(t.getMessage())
        case Success(r) => r.result match {
          case Left(t) => fail(t.getMessage())
          case Right(f) =>
            if (!f._2) fail("Error writing container directory")
            else {
              readContainerDirectory(ctProxy, "blended_node_0", "/opt/node/data") onComplete {
                case Failure(t) => fail(t.getMessage())
                case Success(cdr) => cdr.result match {
                  case Left(t) => fail(t.getMessage())
                  case Right(cd) =>
                    cd.content.get("data/testFile.txt") match {
                      case None => fail("expected file [/opt/node/data/testFile.txt] not found in container")
                      case Some(c) =>
                        val fContent = FileHelper.readFile("data/testFile.txt")
                        c should equal(fContent)
                    }
                }
              }
            }
        }
      }
    }

    "Allow to execute an arbitrary command on the container" in {

      execContainerCommand(
        ctProxy = ctProxy,
        ctName = "blended_node_0",
        cmdTimeout = 5.seconds,
        user = "blended",
        cmd = "ls -al /opt/node".split(" "): _*
      ) onComplete {
        case Failure(t) => fail(t.getMessage())
        case Success(r) =>
          r.result match {
            case Left(t) => fail(t.getMessage())
            case Right(er) =>
              log.info(s"Command output is [\n${new String(er._2.out)}\n]")
              er._2.rc should be(0)
          }
      }
    }
  }
}