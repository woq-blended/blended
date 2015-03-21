package de.woq.blended.itestsupport.docker

import de.woq.blended.testsupport.TestActorSys
import org.scalatest.mock.MockitoSugar
import org.scalatest.Matchers
import org.scalatest.WordSpecLike
import akka.actor.Props
import de.woq.blended.itestsupport.ContainerUnderTest

class DockerContainerMapperSpec extends TestActorSys
  with WordSpecLike
  with Matchers
  with DockerTestSetup
  with MockitoSugar {
  
  "The DockerContainerMapper" should {
    
    "Respond with un updated list of CUTs upon successful port mappings" in {
      
      val cuts = ContainerUnderTest.containerMap(system.settings.config)
      val mapper = system.actorOf(Props(new DockerContainerMapper))
      
      mapper ! InternalMapDockerContainers(testActor, cuts, mockClient)
      expectMsgType[InternalDockerContainersMapped]
    }
  }

}