package blended.itest.node

import akka.actor.{ActorRef, ActorSystem, Props}
import akka.testkit.TestKit
import akka.util.Timeout
import blended.itestsupport.BlendedIntegrationTestSupport
import org.scalatest.BeforeAndAfterAll
import org.scalatest.refspec.RefSpec

import scala.collection.immutable.IndexedSeq
import scala.concurrent.Await
import scala.concurrent.duration._

class BlendedDemoIntegrationSpec extends RefSpec
  with BeforeAndAfterAll
  with BlendedIntegrationTestSupport {

  implicit val testkit = new TestKit(ActorSystem("Blended"))
  
  private[this] val ctProxy = testkit.system.actorOf(Props(new TestContainerProxy()))
  private[this] implicit val timeout = Timeout(60.seconds)
  
  override def nestedSuites = IndexedSeq(new BlendedDemoSpec(ctProxy: ActorRef))
  
  override def beforeAll() {
    testContext(ctProxy)(timeout, testkit)
    containerReady(ctProxy)(timeout, testkit)
  }
  
  override def afterAll() {

    val logs = Await.result(readContainerDirectory(ctProxy, "blended_node_0", "/opt/node/log"), timeout.duration)
    saveContainerDirectory(s"$testOutput/testlog", logs)

    stopContainers(ctProxy)(timeout, testkit)
  }
}
