package blended.itestsupport.docker

import akka.actor.Props
import akka.testkit.TestProbe
import blended.itestsupport.ContainerUnderTest
import blended.testsupport.TestActorSys
import org.scalatestplus.mockito.MockitoSugar
import scala.concurrent.duration._

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class DockerContainerMapperSpec extends AnyWordSpec
  with Matchers
  with DockerTestSetup
  with MockitoSugar {

  "The DockerContainerMapper" should {
    
    "Respond with an updated list of CUTs upon successful port mappings" in TestActorSys { testkit =>
      implicit val system = testkit.system
      val probe = TestProbe()

      val cuts = ContainerUnderTest.containerMap(system.settings.config)
      val mapper = system.actorOf(Props(new DockerContainerMapperActor))
      
      mapper.tell(InternalMapDockerContainers(probe.ref, cuts, mockClient), probe.ref)
      
      val msg = probe.receiveOne(3.seconds).asInstanceOf[InternalDockerContainersMapped]
      
      msg.result match {
        case Left(t) => fail(t)
        case Right(container) =>
          val jmsDemo = container("jms_demo")
          jmsDemo.ports should have size 3
          
          jmsDemo.ports("jms").privatePort should be(1883)
          jmsDemo.ports("jms").publicPort should be(45004) 
          
          jmsDemo.ports("jmx").privatePort should be(1099)
          jmsDemo.ports("jmx").publicPort should be(45003) 

          jmsDemo.ports("http").privatePort should be(8181)
          jmsDemo.ports("http").publicPort should be(45005) 

          jmsDemo.dockerName should be("jms_demo_0")
          
          val blendedDemo = container("blended_demo")
          blendedDemo.ports should have size 2
          
          blendedDemo.ports("jmx").privatePort should be(1099)
          blendedDemo.ports("jmx").publicPort should be(45000)
          
          blendedDemo.ports("http").privatePort should be(8181)
          blendedDemo.ports("http").publicPort should be(45002)
          
          blendedDemo.dockerName should be("blended_demo_0")
      }
    }
  }
}
