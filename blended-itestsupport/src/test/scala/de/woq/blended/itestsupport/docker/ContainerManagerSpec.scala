package de.woq.blended.itestsupport.docker

import akka.actor.Props
import akka.testkit.TestActorRef
import de.woq.blended.testsupport.TestActorSys
import org.scalatest.mock.MockitoSugar
import org.scalatest.{WordSpecLike, Matchers}
import scala.concurrent.duration._

import de.woq.blended.itestsupport.docker.protocol._

class ContainerManagerSpec extends TestActorSys
  with WordSpecLike
  with Matchers
  with DockerTestSetup
  with MockitoSugar {

  object TestContainerManager {
    def apply() = new  ContainerManager with DockerClientProvider {
      override def getClient = mockClient
    }
  }

  "The ContainerManager" should {

    "Respond with an event after all containers have been started" in {

      val mgr = TestActorRef(Props(TestContainerManager()), "mgr")
      mgr ! StartContainerManager
      expectMsg(30.seconds, ContainerManagerStarted)
    }
  }
}
