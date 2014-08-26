package de.woq.blended.itestsupport.docker

import java.util
import java.util.UUID

import com.github.dockerjava.api.DockerClient
import com.github.dockerjava.api.command._
import com.github.dockerjava.api.model._
import org.mockito.Mockito._
import org.scalatest.mock.MockitoSugar

trait DockerTestSetup { this : MockitoSugar =>

  implicit val mockClient = mock[DockerClient]

  val portBindings = new Ports()
  val ctNames  = Seq("blended_demo_0", "jms_demo_0")
  val imageIds = ctNames.map( _ -> UUID.randomUUID().toString).toMap
  val images   = ctNames.map( _ -> mock[Image] ).toMap

  val ctName = ctNames(0)
  val imageId = imageIds.get(ctName).get

  ctNames.foreach { name =>
    when(images.get(name).get.getId) thenReturn(imageIds.get(name).get)
    when(images.get(name).get.getRepoTags).thenReturn(Array(s"atooni/${name}"))
  }

  val createResp = mock[CreateContainerResponse]
  val createCmd = mock[CreateContainerCmd]
  when(createCmd.exec()) thenReturn(createResp)
  when(createCmd.withTty(true)) thenReturn(createCmd)

  val listImgCmd = mock[ListImagesCmd]
  val imgList = new util.ArrayList[Image]
  images.values.foreach(imgList.add(_))
  when(listImgCmd.exec()) thenReturn(imgList)
  when(mockClient.listImagesCmd()) thenReturn(listImgCmd)

  val waitCmd = mock[WaitContainerCmd]
  val stopCmd = mock[StopContainerCmd]
  val startCmd = mock[StartContainerCmd]
  when(startCmd.withPortBindings(portBindings)) thenReturn(startCmd)
  when(startCmd.withLinks(Link.parse("jms_demo_0:jms_demo"))) thenReturn(startCmd)

  val inspectCmd = mock[InspectContainerCmd]
  val containerInfo = mock[InspectContainerResponse]
  when(inspectCmd.exec()) thenReturn(containerInfo)

  ctNames.foreach { name =>
    when(mockClient.createContainerCmd(imageIds.get(name).get)) thenReturn(createCmd)
    when(createCmd.withName(name)) thenReturn(createCmd)
    when(mockClient.waitContainerCmd(name)).thenReturn(waitCmd)
    when(mockClient.stopContainerCmd(name)).thenReturn(stopCmd)
    when(mockClient.startContainerCmd(name)).thenReturn(startCmd)
    when(mockClient.inspectContainerCmd(name)).thenReturn(inspectCmd)
  }

  val listContainersCmd = mock[ListContainersCmd]
  when(mockClient.listContainersCmd()) thenReturn(listContainersCmd)
  when(listContainersCmd.withShowAll(true)) thenReturn(listContainersCmd)
  when(listContainersCmd.exec()) thenReturn(new util.ArrayList[Container]())
}
