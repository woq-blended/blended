package blended.itestsupport.docker

import java.util
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

import com.github.dockerjava.api.DockerClient
import com.github.dockerjava.api.command._
import com.github.dockerjava.api.model._
import com.typesafe.config.ConfigFactory
import org.mockito.Matchers._
import org.mockito.Mockito._
import org.scalatest.mockito.MockitoSugar

import scala.collection.convert.WrapAsJava._

trait DockerTestSetup { this : MockitoSugar =>
  
  implicit val mockClient = mock[DockerClient]
  
  val config = ConfigFactory.load()

  // Mock some containers for the docker API 
  val ctNames      = Seq("blended_demo_0", "jms_demo_0")
  val ctImageNames = ctNames.map( name => (name, configureMockContainer(name)) ).toMap
  val imgList : java.util.List[Image] = ctImageNames.map { case (ctName, id) =>
    val img = mock[Image]
    when(img.getId) thenReturn(id)
    when(img.getRepoTags) thenReturn Array ( s"atooni/$ctName:latest" )
    img
  }.toSeq 
  
  val portNumber = new AtomicInteger(45000)
  
  val listImgCmd = mock[ListImagesCmd]
  
  when(mockClient.listImagesCmd()) thenReturn listImgCmd
  when(listImgCmd.exec()) thenReturn (imgList)

  val listContainersCmd = mock[ListContainersCmd]
  when(mockClient.listContainersCmd()) thenReturn listContainersCmd
  when(listContainersCmd.withShowAll(true)) thenReturn listContainersCmd
  
  val running = new util.ArrayList[Container]()
  ctNames.foreach { ctName => 
    val ct = mock[Container]
    when(ct.getImage) thenReturn s"atooni/$ctName:latest"
    when(ct.getNames) thenReturn Array ( s"/$ctName", s"/foo/$ctName" )
    
    val ports = configurePorts(ct)
    when(ct.getPorts) thenReturn ports
    
    running.add(ct)
  }
  
  when(listContainersCmd.exec()) thenReturn running
  
  def configurePorts(ct: Container) : Array[ContainerPort] = {
  
    // Expose 3 ports per mocked docker container 
    val ports : List[Int] = List( 1099, 1883, 8181 )
    
    val ctPorts = ports.map { p => 
      val ctPort = mock[ContainerPort]
      
      when(ctPort.getIp) thenReturn("0.0.0.0")
      when(ctPort.getPublicPort) thenReturn(portNumber.getAndIncrement)
      when(ctPort.getPrivatePort) thenReturn(p)
      
      ctPort
    }
    
    ctPorts.toArray
  }
  
  // Set up a mock for an individual container
  def configureMockContainer(ctName : String) : String = {
    
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

    when(mockClient.createContainerCmd(anyString)) thenReturn createCmd
    when(createCmd.withName(anyString())) thenReturn createCmd
    when(createCmd.withPortBindings(anyVararg[PortBinding]())) thenReturn createCmd
    when(createCmd.withLinks(Link.parse("jms_demo_0:jms_demo"))) thenReturn createCmd
    when(createCmd.withEnv(anyVararg[String]())) thenReturn createCmd
    when(createCmd.withPublishAllPorts(true)) thenReturn createCmd

    when(mockClient.waitContainerCmd(anyString())) thenReturn waitCmd
    when(mockClient.stopContainerCmd(anyString())) thenReturn stopCmd
    when(mockClient.startContainerCmd(anyString())) thenReturn startCmd
    when(mockClient.inspectContainerCmd(anyString())) thenReturn inspectCmd

    when(inspectCmd.exec()) thenReturn containerInfo
    
    imageId
  }
}
