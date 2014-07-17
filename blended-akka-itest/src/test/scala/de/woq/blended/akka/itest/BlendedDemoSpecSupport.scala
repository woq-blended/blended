package de.woq.blended.akka.itest

import akka.util.Timeout
import akka.pattern.ask
import de.woq.blended.itestsupport.BlendedIntegrationTestSupport
import de.woq.blended.itestsupport.docker.protocol._
import de.woq.blended.testsupport.TestActorSys
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import scala.concurrent.duration._
import scala.util.{Failure, Success}

class BlendedDemoSpecSupport extends TestActorSys
  with WordSpecLike
  with Matchers
  with BeforeAndAfterAll
  with BlendedIntegrationTestSupport {

  implicit val timeOut = new Timeout(3.seconds)
  implicit val eCtxt = system.dispatcher

  val log = system.log

  "The demo container" should {

    "do something" in {
      containerMgr.map { mgr =>
        (mgr ? GetContainerPorts("blended_demo_0")).mapTo[ContainerPorts].onComplete {
          case Success(port) => log info port.toString
          case Failure(cause) => fail(cause)
        }
      }
    }
  }

  override protected def beforeAll() {
    startContainer(30.seconds) should be (ContainerManagerStarted)
  }
}
