package blended.itestsupport

import akka.actor.{ActorRef, Props}
import akka.pattern.ask
import akka.testkit.{TestKit, TestProbe}
import akka.util.Timeout
import akka.util.Timeout.durationToTimeout
import blended.itestsupport.condition.{Condition, ConditionActor}
import blended.itestsupport.docker.protocol._
import blended.itestsupport.protocol._
import org.apache.camel.CamelContext

import scala.concurrent.Await

trait BlendedIntegrationTestSupport {

  def testContext(ctProxy: ActorRef)(implicit timeout: Timeout, testKit: TestKit) : CamelContext = {
    val probe = new TestProbe(testKit.system)
    val cuts = ContainerUnderTest.containerMap(testKit.system.settings.config)
    ctProxy.tell(TestContextRequest(cuts), probe.ref)
    probe.receiveN(1,timeout.duration).head.asInstanceOf[CamelContext]
  }
  
  def containerReady(ctProxy: ActorRef)(implicit timeout: Timeout, testKit : TestKit) : Unit = {
    val probe = new TestProbe(testKit.system)
    ctProxy.tell(ContainerReady_?, probe.ref)
    probe.expectMsg(timeout.duration, ContainerReady(true))
  }
  
  def stopContainers(ctProxy: ActorRef)(implicit timeout: Timeout, testKit: TestKit) : Unit = {
    val probe = new TestProbe(testKit.system)
    testKit.system.log.debug(s"stopProbe [${probe.ref}]")
    ctProxy.tell(new StopContainerManager(timeout.duration), probe.ref)
    probe.expectMsg(timeout.duration, ContainerManagerStopped)
  }

  def containerDirectory(ctProxy: ActorRef, container: String, dirName: String)(implicit timeout: Timeout, testKit: TestKit) : ContainerDirectory = {

    val future = ctProxy.ask(GetContainerDirectory(container, dirName)).mapTo[ContainerDirectory]
    Await.result(future, timeout.duration)
  }

  def assertCondition(condition: Condition)(implicit testKit: TestKit) : Boolean = {

    implicit val eCtxt = testKit.system.dispatcher

    val checker = testKit.system.actorOf(Props(ConditionActor(condition)))

    val checkFuture = (checker ? CheckCondition)(condition.timeout).map { result =>
      result match {
        case cr: ConditionCheckResult => cr.allSatisfied
        case _ => false
      }
    }

    Await.result(checkFuture, condition.timeout)
  }
}
