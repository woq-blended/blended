package de.woq.blended.itestsupport.docker

import java.util
import java.util.UUID

import com.github.dockerjava.client.DockerClient
import com.github.dockerjava.client.command._
import com.github.dockerjava.client.model.{Image, ContainerCreateResponse, Ports}
import org.mockito.Mockito._
import org.scalatest.mock.MockitoSugar

trait DockerTestSetup { this : MockitoSugar =>

  val imageId = UUID.randomUUID().toString
  val ctName  = "blended_demo_0"

  val portBindings = new Ports()

  val image = mock[Image]
  when(image.getId).thenReturn(imageId)
  when(image.getRepoTags).thenReturn(Array("atooni/blended_demo"))

  val createResp = mock[ContainerCreateResponse]
  val createCmd = mock[CreateContainerCmd]
  when(createCmd.exec()) thenReturn(createResp)

  val listImgCmd = mock[ListImagesCmd]
  val imgList = new util.ArrayList[Image]
  imgList.add(image)
  when(listImgCmd.exec()).thenReturn(imgList)

  val waitCmd = mock[WaitContainerCmd]
  val stopCmd = mock[StopContainerCmd]
  val startCmd = mock[StartContainerCmd]
  when(startCmd.withPortBindings(portBindings)) thenReturn(null)

  implicit val mockClient = mock[DockerClient]
  when(mockClient.createContainerCmd(imageId)) thenReturn(createCmd)
  when(createCmd.withName(ctName)) thenReturn(createCmd)
  when(mockClient.waitContainerCmd(ctName)).thenReturn(waitCmd)
  when(mockClient.stopContainerCmd(ctName)).thenReturn(stopCmd)
  when(mockClient.startContainerCmd(ctName)).thenReturn(startCmd)
  when(mockClient.listImagesCmd()).thenReturn(listImgCmd)
}
