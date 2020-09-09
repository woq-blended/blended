package blended.itestsupport.docker

import akka.actor.Props
import akka.testkit.{TestActorRef, TestProbe}
import blended.itestsupport.ContainerUnderTest
import blended.itestsupport.docker.protocol._
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.mockito.MockitoSugar
import akka.actor.ActorSystem

class ContainerManagerSpec extends AnyWordSpec
  with DockerTestSetup
  with MockitoSugar {
  
  object TestContainerManager {
    def apply() = new  ContainerManagerActor with DockerClientProvider {
      override def getClient = mockClient
    }
  }

  "The ContainerManager" should {

    "Respond with an event after all containers have been started" in {
      implicit val system : ActorSystem = ActorSystem("ContainerManager")
      val probe = TestProbe()

      val cuts = ContainerUnderTest.containerMap(system.settings.config)
      
      system.log.info(s"$cuts")
      
      val mgr = TestActorRef(Props(TestContainerManager()), "mgr")
      mgr.tell(StartContainerManager(cuts), probe.ref)
      
      probe.expectMsgType[ContainerManagerStarted]
    }
  }
}
