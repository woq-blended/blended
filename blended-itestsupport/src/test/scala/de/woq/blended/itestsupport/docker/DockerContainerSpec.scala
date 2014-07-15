package de.woq.blended.itestsupport.docker

import org.mockito.Mockito._
import org.scalatest.mock.MockitoSugar
import org.scalatest.{Matchers, WordSpec}


class DockerContainerSpec extends WordSpec
  with Matchers
  with DockerTestSetup
  with MockitoSugar {

  "A Docker Container should" should {

    "be created from the image id and a name" in {
      val container = new DockerContainer(imageId, ctName)

      verify(mockClient).createContainerCmd(imageId)
      verify(createCmd).withName(ctName)
    }

    "issue the wait command with the correct id" in {
      val container = new DockerContainer(imageId, ctName)
      container.waitContainer

      verify(mockClient).waitContainerCmd(ctName)
    }
    
    "issue the stop command with the correct id" in {
      val container = new DockerContainer(imageId, ctName)
      container.stopContainer

      verify(mockClient).stopContainerCmd(ctName)
    }

    "issue the start command with the correct id" in {
      val container = new DockerContainer(imageId, ctName)
      container.startContainer(portBindings)

      verify(mockClient).startContainerCmd(ctName)
    }

    "allow to set the linked containers" in {
      val container = new DockerContainer(imageId, ctName)
      container.withLink("foo").withLink("bar")

      val links = container.links

      links should contain theSameElementsAs Vector("foo", "bar")
    }

    "allow to set single exposed ports" in {
      val container = new DockerContainer(imageId, ctName)
      val namedPort : NamedContainerPort = ("jmx", 1099)
      container.withNamedPort(namedPort)

      val ports = container.ports should be (Map("jmx" -> namedPort))
    }

    "allow to set multiple exposed ports" in {
      val container = new DockerContainer(imageId, ctName)
      val port1 : NamedContainerPort = ("jmx", 1099)
      val port2 : NamedContainerPort = ("http", 8181)
      container.withNamedPort(port1).withNamedPort(port2)

      val ports = container.ports should be (Map("jmx" -> port1, "http" -> port2))
    }

    "allow to set multiple exposed ports at once" in {
      val container = new DockerContainer(imageId, ctName)
      val port1 : NamedContainerPort = ("jmx", 1099)
      val port2 : NamedContainerPort = ("http", 8181)
      container.withNamedPorts(Seq(port1, port2))

      val ports = container.ports should be (Map("jmx" -> port1, "http" -> port2))
    }
  }
}
