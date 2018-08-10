package blended.itestsupport

import java.net.{ServerSocket, Socket}
import java.util.concurrent.atomic.AtomicInteger

import com.github.dockerjava.api.model.PortBinding
import com.typesafe.config.Config

import scala.collection.JavaConverters._
import scala.util.control.NonFatal

object NamedContainerPort {

  private[this] val portCount = new AtomicInteger(32768)

  private[this] def nextFreePort() : Int = {

    def isFree(p : Int) : Boolean = {

      try {
        val socket : ServerSocket = new ServerSocket(p)
        socket.close()
        true
      } catch {
        case NonFatal(_) => false
      }
    }

    var result = portCount.getAndIncrement()
    while (!isFree(result)) result = portCount.getAndIncrement()

    result
  }


  def apply(config : Config) : NamedContainerPort = {
    val privatePort = config.getInt("private")
    val publicPort = if (config.hasPath("public")) 
      config.getInt("public")
    else
      nextFreePort()

    NamedContainerPort(config.getString("name"), privatePort, publicPort)
  }   
}

case class NamedContainerPort(
  name: String, 
  privatePort: Int,
  publicPort: Int
) {
  def binding = PortBinding.parse(s"$publicPort:$privatePort")
}

object VolumeConfig {
  def apply(config : Config) : VolumeConfig = VolumeConfig(
    config.getString("host"), 
    config.getString("container")
  )  
}

case class VolumeConfig(
  hostDirectory : String,
  containerDirectory : String
)

object ContainerLink {
  def apply(config: Config) : ContainerLink = ContainerLink(
    config.getString("container"), 
    config.getString("hostname")
  )
}

case class ContainerLink(
  container : String,
  hostname  : String
)

object ContainerUnderTest {

  def containerMap(config: Config) : Map[String, ContainerUnderTest] = config.getConfigList("docker.containers").asScala.map { cfg =>
      ContainerUnderTest(cfg)
    }.toList.map( ct => (ct.ctName, ct)).toMap
      
  def apply(config : Config) : ContainerUnderTest = {
    
    val volumes : List[VolumeConfig] = if (config.hasPath("volumes"))
      config.getConfigList("volumes").asScala.map{cfg: Config => VolumeConfig(cfg)}.toList
    else
      List.empty
    
    val ports : List[NamedContainerPort] = if (config.hasPath("ports"))
      config.getConfigList("ports").asScala.map { cfg: Config => NamedContainerPort(cfg) }.toList
    else 
      List.empty
    
    val links : List[ContainerLink] = if (config.hasPath("links"))
      config.getConfigList("links").asScala.map { cfg: Config => ContainerLink(cfg) }.toList
    else 
      List.empty
    
    val ctName = config.getString("name")
    
    val dockerName : String = if (config.hasPath("dockerName")) 
      config.getString("dockerName")
    else 
      s"${ctName}_${System.currentTimeMillis}"

    val env : Map[String, String] = if (config.hasPath("env")) {
      config.getConfig("env").entrySet().asScala.map { entry =>
        (entry.getKey(), config.getConfig("env").getString(entry.getKey()))
      }.toMap
    } else Map.empty

    ContainerUnderTest(
      ctName = config.getString("name"),
      imgPattern = config.getString("image"),
      imgId = dockerName,
      dockerName = dockerName,
      volumes = volumes,
      links = links,
      ports = ports.map { p => (p.name, p) }.toMap,
      env = env
    )
  }
} 

case class ContainerUnderTest(
  ctName          : String,
  imgPattern      : String,
  imgId           : String,
  dockerName      : String,
  volumes         : List[VolumeConfig] = List.empty,
  links           : List[ContainerLink] = List.empty,                             
  ports           : Map[String, NamedContainerPort] = Map.empty,
  env             : Map[String, String] = Map.empty
) {
  
  val DEFAULT_PROTOCOL = "tcp"
  
  def url(
    portName: String,
    host: String = "127.0.0.1",
    protocol: String = DEFAULT_PROTOCOL,
    user: Option[String] = None,
    pwd: Option[String] = None
  ) : String = {
    val port = ports.get(portName) match {
      case None => 65000
      case Some(p) => p.publicPort
    }

    val cred = (user, pwd) match {
      case (None, _) => ""
      case (Some(u), None) => s"$u@"
      case (Some(u), Some(p)) => s"$u:$p@"
    }

    s"$protocol://$cred$host:$port"
  }
}
