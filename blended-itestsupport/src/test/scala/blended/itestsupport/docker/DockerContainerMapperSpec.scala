/*
 * Copyright 2014ff,  https://github.com/woq-blended
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

package blended.itestsupport.docker

import akka.actor.Props
import blended.itestsupport.ContainerUnderTest
import blended.testsupport.TestActorSys
import org.scalatest.{Matchers, WordSpecLike}
import org.scalatest.mock.MockitoSugar
import org.slf4j.LoggerFactory

import scala.concurrent.duration._

class DockerContainerMapperSpec extends TestActorSys
  with WordSpecLike
  with Matchers
  with DockerTestSetup
  with MockitoSugar {
  
  private[this] val log = LoggerFactory.getLogger(classOf[DockerContainerMapperSpec])
  
  "The DockerContainerMapper" should {
    
    "Respond with an updated list of CUTs upon successful port mappings" in {
      
      val cuts = ContainerUnderTest.containerMap(system.settings.config)
      val mapper = system.actorOf(Props(new DockerContainerMapper))
      
      mapper ! InternalMapDockerContainers(testActor, cuts, mockClient)
      
      val msg = receiveOne(3.seconds).asInstanceOf[InternalDockerContainersMapped]
      
      msg.result match {
        case Left(t) => fail(t)
        case Right(cuts) => 
          val jmsDemo = cuts("jms_demo")
          jmsDemo.ports should have size(3)
          
          jmsDemo.ports("jms").privatePort should be(1883)
          jmsDemo.ports("jms").publicPort should be(45004) 
          
          jmsDemo.ports("jmx").privatePort should be(1099)
          jmsDemo.ports("jmx").publicPort should be(45003) 

          jmsDemo.ports("http").privatePort should be(8181)
          jmsDemo.ports("http").publicPort should be(45005) 

          jmsDemo.dockerName should be("jms_demo_0")
          
          val blendedDemo = cuts("blended_demo")
          blendedDemo.ports should have size(2)
          
          blendedDemo.ports("jmx").privatePort should be(1099)
          blendedDemo.ports("jmx").publicPort should be(45000)
          
          blendedDemo.ports("http").privatePort should be(8181)
          blendedDemo.ports("http").publicPort should be(45002)
          
          blendedDemo.dockerName should be("blended_demo_0")
      }
    }
  }
}
