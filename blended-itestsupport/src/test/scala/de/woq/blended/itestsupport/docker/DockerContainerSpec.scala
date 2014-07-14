package de.woq.blended.itestsupport.docker

import com.github.dockerjava.client.DockerClient
import com.github.dockerjava.client.command.CreateContainerCmd
import com.github.dockerjava.client.model.ContainerCreateResponse
import org.scalatest.mock.MockitoSugar
import org.mockito.Mockito._
import org.scalatest.{Matchers, WordSpec}


class DockerContainerSpec extends WordSpec
  with Matchers
  with MockitoSugar {

  val createResp = mock[ContainerCreateResponse]
  val createCmd = mock[CreateContainerCmd]
  when(createCmd.exec()) thenReturn(createResp)

  implicit val client = mock[DockerClient]
  when(client.createContainerCmd("test")) thenReturn(createCmd)
  when(createCmd.withName("myHost")) thenReturn(createCmd)

  "A Docker Container should" should {

    "be created from the image id and a name" in {
      val container = new DockerContainer("test", "myHost")

      verify(client).createContainerCmd("test")
      verify(createCmd).withName("myHost")
    }
  }
}
