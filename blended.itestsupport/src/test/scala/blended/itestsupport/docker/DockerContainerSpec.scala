package blended.itestsupport.docker

import blended.itestsupport.ContainerUnderTest
import blended.util.logging.Logger
import org.mockito.Mockito._
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.mockito.MockitoSugar

class DockerContainerSpec extends AnyWordSpec
  with DockerTestSetup
  with MockitoSugar {
  
  private[this] val log = Logger[DockerContainerSpec]

  "A Docker Container should" should {

    val cuts = ContainerUnderTest.containerMap(config)
    val cut = cuts("blended_demo")
    
    log.info(s"$cut")

    "be created from the image id and a name" in {
      new DockerContainer(cut)
    }

    "issue the stop command with the correct id" in {
      val container = new DockerContainer(cut)
      container.stopContainer

      verify(mockClient).stopContainerCmd(cut.dockerName)
    }

    "issue the start command with the correct id" in {
      val container = new DockerContainer(cut)
      container.startContainer

      verify(mockClient).startContainerCmd(cut.dockerName)
      val createCmd = mockClient.createContainerCmd(cut.imgId)
      verify(createCmd).withName(cut.dockerName)
    }

    "issue the InspectContainerCommand with the correct id" in {
      val container = new DockerContainer(cut)
      container.containerInfo

      verify(mockClient).inspectContainerCmd(cut.dockerName)
    }
  }
}
