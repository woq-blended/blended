package blended.itest.node

import akka.actor.{ActorRef, ActorSystem, Props}
import scala.util.Failure
import scala.util.Success
import akka.testkit.TestKit
import akka.util.Timeout
import blended.itestsupport.BlendedIntegrationTestSupport
import blended.util.logging.Logger
import org.scalatest.BeforeAndAfterAll
import org.scalatest.refspec.RefSpec

import scala.collection.immutable.IndexedSeq
import scala.concurrent.Await
import scala.concurrent.duration._

class BlendedDemoIntegrationSpec extends RefSpec
  with BeforeAndAfterAll
  with BlendedIntegrationTestSupport {

  implicit val testkit = new TestKit(ActorSystem("Blended"))
  implicit val eCtxt = testkit.system.dispatcher

  private[this] val log = Logger[BlendedDemoIntegrationSpec]

  private[this] val ctProxy = testkit.system.actorOf(Props(new TestContainerProxy()))
  private[this] implicit val timeout = Timeout(60.seconds)
  
  override def nestedSuites = IndexedSeq(new BlendedDemoSpec(ctProxy: ActorRef))

  override def beforeAll() {
    log.info(s"Using testkit [${testkit}]")
    testContext(ctProxy)(timeout, testkit)
    containerReady(ctProxy)(timeout, testkit)
  }
  
  override def afterAll() {

    val ctr = "blended_node_0"
    val dir = "/opt/node/log"

    readContainerDirectory(ctProxy, ctr, dir).onComplete {
      case Success(cdr) => cdr.result match {
        case Left(_) =>
        case Right(cd) =>
          val outputDir = s"${testOutput}/testlog"
          saveContainerDirectory(outputDir, cd)
          log.info(s"Saved container output to [${outputDir}]")

      }
      case Failure(e) =>
        log.error(e)(s"Could not read containder directory [${dir}] of container [${ctr}]")
    }

    stopContainers(ctProxy)(timeout, testkit)
  }
}
