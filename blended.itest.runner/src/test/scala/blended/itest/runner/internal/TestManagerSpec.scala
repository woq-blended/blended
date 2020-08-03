package blended.itest.runner.internal

import org.scalatest.matchers.should.Matchers
import akka.testkit.TestKit
import blended.testsupport.scalatest.LoggingFreeSpecLike
import akka.actor.ActorSystem
import akka.testkit.TestProbe
import blended.itest.runner.Protocol
import akka.actor.ActorRef
import akka.actor.Props
import blended.itest.runner.TestFactory
import scala.concurrent.duration._
import blended.itest.runner.Protocol.TestFactories
import blended.itest.runner.Protocol.AddTestFactory

class TestManagerSpec extends TestKit(ActorSystem("TestManager"))
  with LoggingFreeSpecLike
  with Matchers {

  "The test manager should" - { 
    "start with an empty list of factories" in {

      val probe : TestProbe = TestProbe()
      val mgr : ActorRef = system.actorOf(Props(new TestManager()))

      mgr.tell(Protocol.GetTestFactories, probe.ref)

      probe.expectMsg(Protocol.TestFactories(List.empty))
      system.stop(mgr)
    }

    "Allow to add / remove a test factory" in { 

      val f : TestFactory = new TestFactory() {
        override def name: String = "myFactory"
      }


      val probe : TestProbe = TestProbe()
      val mgr : ActorRef = system.actorOf(Props(new TestManager()))

      mgr ! AddTestFactory(f)
      mgr.tell(Protocol.GetTestFactories, probe.ref)

      probe.fishForMessage(1.second) {
        case TestFactories(l) => l.map(_.name).equals(List("myFactory"))
      }

      mgr ! Protocol.RemoveTestFactory(f.name)
      mgr.tell(Protocol.GetTestFactories, probe.ref)
      probe.expectMsg(Protocol.TestFactories(List.empty))

      system.stop(mgr)
    }
  }  
}
