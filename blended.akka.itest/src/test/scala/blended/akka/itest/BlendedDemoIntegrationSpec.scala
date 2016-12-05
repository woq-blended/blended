package blended.akka.itest

import akka.actor.{ActorSystem, Props}
import akka.testkit.TestKit
import blended.itestsupport.BlendedIntegrationTestSupport
import org.scalatest.{BeforeAndAfterAll, Spec}

import scala.collection.immutable.IndexedSeq
import scala.concurrent.duration._

class BlendedDemoIntegrationSpec extends Spec
  with BeforeAndAfterAll
  with BlendedIntegrationTestSupport {

  implicit val testkit = new TestKit(ActorSystem("Blended"))
  
  private[this] val ctProxy = testkit.system.actorOf(Props(new TestContainerProxy()))
  private[this] val timeout = 1200.seconds
  
  override def nestedSuites = IndexedSeq(new BlendedDemoSpec())
  
  override def beforeAll() {
    testContext(ctProxy)(timeout, testkit)
    containerReady(ctProxy)(timeout, testkit)
  }
  
  override def afterAll() {
    stopContainers(ctProxy)(1200.seconds, testkit)
  }
}
