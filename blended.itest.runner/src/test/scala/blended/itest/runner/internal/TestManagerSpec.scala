package blended.itest.runner.internal

import org.scalatest.matchers.should.Matchers
import akka.testkit.TestKit
import blended.testsupport.scalatest.LoggingFreeSpecLike
import akka.actor.ActorSystem
import akka.testkit.TestProbe
import blended.itest.runner.Protocol
import akka.actor.ActorRef
import akka.actor.Props
import blended.itest.runner.TestTemplate
import scala.concurrent.duration._
import blended.itest.runner.Protocol.TestTemplates
import blended.itest.runner.Protocol.AddTestTemplate
import org.scalatest.BeforeAndAfterAll
import scala.concurrent.Await
import scala.util.Try

class TestManagerSpec extends TestKit(ActorSystem("TestManager"))
  with LoggingFreeSpecLike
  with Matchers 
  with BeforeAndAfterAll {

  private def template() : TestTemplate = new TestTemplate() { 
    override val name : String = "myFactory"
    override def test() : Try[Unit] = Try{}
  }  

  override protected def afterAll(): Unit = {
    Await.result(system.terminate(), 10.seconds)
  }

  "The test manager should" - { 
    "start with an empty list of templates" in {

      val probe : TestProbe = TestProbe()
      val mgr : ActorRef = system.actorOf(Props(new TestManager()))

      mgr.tell(Protocol.GetTestTemplates, probe.ref)

      probe.expectMsg(Protocol.TestTemplates(List.empty))
      system.stop(mgr)
    }

    "Allow to add / remove a test template" in { 

      val f : TestTemplate = template()

      val probe : TestProbe = TestProbe()
      val mgr : ActorRef = system.actorOf(Props(new TestManager()))

      mgr ! AddTestTemplate(f)
      mgr.tell(Protocol.GetTestTemplates, probe.ref)

      probe.fishForMessage(1.second) {
        case TestTemplates(l) => l.map(_.name).equals(List("myFactory"))
      }

      mgr ! Protocol.RemoveTestTemplate(f.name)
      mgr.tell(Protocol.GetTestTemplates, probe.ref)
      probe.expectMsg(Protocol.TestTemplates(List.empty))

      system.stop(mgr)
    }
  }  
}
