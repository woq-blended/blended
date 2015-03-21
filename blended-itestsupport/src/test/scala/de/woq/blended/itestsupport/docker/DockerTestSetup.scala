/*
 * Copyright 2014ff, WoQ - Way of Quality GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.woq.blended.itestsupport.docker

import java.util
import java.util.UUID
import com.github.dockerjava.api.DockerClient
import com.github.dockerjava.api.command._
import com.github.dockerjava.api.model._
import com.typesafe.config.ConfigFactory
import org.mockito.Mockito._
import org.scalatest.mock.MockitoSugar
import org.slf4j.LoggerFactory
import scala.collection.convert.Wrappers.JListWrapper
import scala.collection.convert.Wrappers.JListWrapper
import scala.collection.convert.Wrappers.JListWrapper
import java.util.ArrayList

trait DockerTestSetup { this : MockitoSugar =>
  
  implicit val mockClient = mock[DockerClient]
  
  val config = ConfigFactory.load()

  // Mock some containers for the docker API 
  val ctNames      = Seq("blended_demo_0", "jms_demo_0")
  val ctImageNames = ctNames.map( name => (name, configureMockContainer(name)) ).toMap
  
  val listImgCmd = mock[ListImagesCmd]
  
  //when(listImgCmd.exec()) thenReturn (imageList(ctImageNames))
  when(mockClient.listImagesCmd()) thenReturn listImgCmd

  val listContainersCmd = mock[ListContainersCmd]
  when(mockClient.listContainersCmd()) thenReturn listContainersCmd
  when(listContainersCmd.withShowAll(true)) thenReturn listContainersCmd
  when(listContainersCmd.exec()) thenReturn new util.ArrayList[Container]()
  
  // Create the proper image lists for the mock 
//  def imageList(ctImageNames : Map[String, String]) : java.util.List[Image] = {
//    val result = new java.util.ArrayList[Image]()
//    
//    ctImageNames.map { 
//      case (ctName, imageId) =>
//        val image = mock[Image]
//        when(image.getId) thenReturn imageId
//        when(image.getRepoTags) thenReturn (Array(s"atooni/$ctName:latest"))
//        result.add(image)
//      }
//    
//    result
//  }
//  
  // Set up a mock for an individual container
  def configureMockContainer(ctName : String) : String = {
    
    val portBindings = new Ports()
    val imageId = UUID.randomUUID().toString()
    
    val createResp = mock[CreateContainerResponse]
    val createCmd = mock[CreateContainerCmd]
    when(createCmd.exec()) thenReturn createResp
    when(createCmd.withTty(true)) thenReturn createCmd

    val waitCmd = mock[WaitContainerCmd]
    val stopCmd = mock[StopContainerCmd]
    val startCmd = mock[StartContainerCmd]
    val inspectCmd = mock[InspectContainerCmd]
    val containerInfo = mock[InspectContainerResponse]

    when(mockClient.createContainerCmd(imageId)) thenReturn createCmd
    when(createCmd.withName(ctName)) thenReturn createCmd
    when(mockClient.waitContainerCmd(ctName)) thenReturn waitCmd
    when(mockClient.stopContainerCmd(ctName)) thenReturn stopCmd
    when(mockClient.startContainerCmd(ctName)) thenReturn startCmd
    when(mockClient.inspectContainerCmd(ctName)) thenReturn inspectCmd

    when(startCmd.withPortBindings(portBindings)) thenReturn startCmd
    when(startCmd.withLinks(Link.parse("jms_demo_0:jms_demo"))) thenReturn startCmd
    when(startCmd.withPublishAllPorts(true)) thenReturn startCmd

    when(inspectCmd.exec()) thenReturn containerInfo
    
    imageId
  }
}
