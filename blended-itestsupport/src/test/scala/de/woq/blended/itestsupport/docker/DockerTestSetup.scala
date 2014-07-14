package de.woq.blended.itestsupport.docker

import com.github.dockerjava.client.DockerClient
import com.github.dockerjava.client.command.{StartContainerCmd, StopContainerCmd, WaitContainerCmd, CreateContainerCmd}
import com.github.dockerjava.client.model.{ContainerCreateResponse, Ports}
import org.mockito.Mockito._
import org.scalatest.mock.MockitoSugar

trait DockerTestSetup { this : MockitoSugar =>

  val imageId = "test"
  val ctName  = "myContainer"

  val portBindings = new Ports()

  val createResp = mock[ContainerCreateResponse]
  val createCmd = mock[CreateContainerCmd]
  when(createCmd.exec()) thenReturn(createResp)

  val waitCmd = mock[WaitContainerCmd]
  val stopCmd = mock[StopContainerCmd]
  val startCmd = mock[StartContainerCmd]
  when(startCmd.withPortBindings(portBindings)) thenReturn(null)

  implicit val client = mock[DockerClient]
  when(client.createContainerCmd(imageId)) thenReturn(createCmd)
  when(createCmd.withName(ctName)) thenReturn(createCmd)
  when(client.waitContainerCmd(ctName)).thenReturn(waitCmd)
  when(client.stopContainerCmd(ctName)).thenReturn(stopCmd)
  when(client.startContainerCmd(ctName)).thenReturn(startCmd)
}
