package blended.itestsupport

import java.util.concurrent.atomic.AtomicInteger

import com.github.dockerjava.api.model.PortBinding
import com.typesafe.config.Config

import scala.collection.convert.Wrappers.JListWrapper

object NamedContainerPort {

  private[this] val portCount = new AtomicInteger(32768)

  def apply(config : Config) : NamedContainerPort = {
    val privatePort = config.getInt("private")
    val publicPort = if (config.hasPath("public")) 
      config.getInt("public")
    else
      portCount.getAndIncrement()

    NamedContainerPort(config.getString("name"), privatePort, publicPort)
  }   
}

case class NamedContainerPort(
  name: String, privatePort: Int, publicPort: Int
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

  def containerMap(config: Config) : Map[String, ContainerUnderTest] = JListWrapper(config.getConfigList("docker.containers")).map { cfg =>
      ContainerUnderTest(cfg)
    }.toList.map( ct => (ct.ctName, ct)).toMap
      
  def apply(config : Config) : ContainerUnderTest = {
    
    val volumes : List[VolumeConfig] = if (config.hasPath("volumes"))
      JListWrapper(config.getConfigList("volumes")).map{cfg: Config => VolumeConfig(cfg)}.toList
    else
      List.empty
    
    val ports : List[NamedContainerPort] = if (config.hasPath("ports"))
      JListWrapper(config.getConfigList("ports")).map { cfg: Config => NamedContainerPort(cfg) }.toList
    else 
      List.empty
    
    val links : List[ContainerLink] = if (config.hasPath("links"))
      JListWrapper(config.getConfigList("links")).map { cfg: Config => ContainerLink(cfg) }.toList
    else 
      List.empty
    
    val ctName = config.getString("name")
    
    val dockerName : String = if (config.hasPath("dockerName")) 
      config.getString("dockerName")
    else 
      s"${ctName}_${System.currentTimeMillis}"

    ContainerUnderTest(
      config.getString("name"),
      config.getString("image"), 
      dockerName,
      dockerName,
      volumes,
      links,
      ports.map { p => (p.name, p) }.toMap
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
  ports           : Map[String, NamedContainerPort] = Map.empty
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
