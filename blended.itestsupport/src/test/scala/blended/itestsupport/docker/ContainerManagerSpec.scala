package blended.itestsupport.docker

import akka.actor.Props
import akka.testkit.{TestActorRef, TestProbe}
import blended.itestsupport.ContainerUnderTest
import blended.itestsupport.docker.protocol._
import blended.testsupport.TestActorSys
import org.scalatestplus.mockito.MockitoSugar
import org.scalatest.{Matchers, WordSpec}

class ContainerManagerSpec extends WordSpec
  with Matchers
  with DockerTestSetup
  with MockitoSugar {
  
  object TestContainerManager {
    def apply() = new  ContainerManagerActor with DockerClientProvider {
      override def getClient = mockClient
    }
  }

  "The ContainerManager" should {

    "Respond with an event after all containers have been started" in TestActorSys { testkit =>
      implicit val system = testkit.system
      val probe = TestProbe()

      val cuts = ContainerUnderTest.containerMap(system.settings.config)
      
      system.log.info(s"$cuts")
      
      val mgr = TestActorRef(Props(TestContainerManager()), "mgr")
      mgr.tell(StartContainerManager(cuts), probe.ref)
      
      probe.expectMsgType[ContainerManagerStarted]
    }
  }
}
