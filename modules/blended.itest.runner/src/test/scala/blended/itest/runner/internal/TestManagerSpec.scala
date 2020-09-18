package blended.itest.runner.internal

import org.scalatest.matchers.should.Matchers
import akka.testkit.TestKit
import blended.testsupport.scalatest.LoggingFreeSpecLike
import akka.actor.ActorSystem
import akka.testkit.TestProbe
import blended.itest.runner.Protocol
import akka.actor.ActorRef
import blended.itest.runner.TestTemplate
import scala.concurrent.duration._
import blended.itest.runner.Protocol.TestTemplates
import blended.itest.runner.Protocol.AddTestTemplateFactory
import org.scalatest.BeforeAndAfterAll
import scala.concurrent.Await
import scala.util.Try
import blended.itest.runner.TestTemplateFactory

class TestManagerSpec extends TestKit(ActorSystem("TestManager"))
  with LoggingFreeSpecLike
  with Matchers
  with BeforeAndAfterAll {

  private def templates() : TestTemplateFactory = new TestTemplateFactory() { f =>

    override val name : String = "myFactory"

    override def templates : List[TestTemplate] = List(
      new TestTemplate() {
        override def factory: TestTemplateFactory = f
        override def name: String = "test-1"
        override def test(id : String) : Try[Unit] = Try{}
      }
    )
  }

  override protected def afterAll(): Unit = {
    Await.result(system.terminate(), 10.seconds)
  }

  "The test manager should" - {
    "start with an empty list of templates" in {

      val probe : TestProbe = TestProbe()
      val mgr : ActorRef = system.actorOf(TestManager.props(5))

      mgr.tell(Protocol.GetTestTemplates, probe.ref)

      probe.expectMsg(Protocol.TestTemplates(List.empty))
      system.stop(mgr)
    }

    "Allow to add / remove a test template" in {

      val f : TestTemplateFactory = templates()

      val probe : TestProbe = TestProbe()
      val mgr : ActorRef = system.actorOf(TestManager.props(5))

      mgr ! AddTestTemplateFactory(f)
      mgr.tell(Protocol.GetTestTemplates, probe.ref)

      probe.fishForMessage(1.second) {
        case TestTemplates(l) => l.map(_.name).equals(List("test-1"))
      }

      mgr ! Protocol.RemoveTestTemplateFactory(f)
      mgr.tell(Protocol.GetTestTemplates, probe.ref)
      probe.expectMsg(Protocol.TestTemplates(List.empty))

      system.stop(mgr)
    }
  }
}
