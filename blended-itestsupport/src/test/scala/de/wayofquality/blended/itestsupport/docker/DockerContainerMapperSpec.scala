package de.wayofquality.blended.itestsupport.docker

import de.wayofquality.blended.testsupport.TestActorSys
import org.scalatest.mock.MockitoSugar
import org.scalatest.Matchers
import org.scalatest.WordSpecLike
import akka.actor.Props
import de.wayofquality.blended.itestsupport.ContainerUnderTest
import scala.concurrent.duration._
import org.slf4j.LoggerFactory

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
