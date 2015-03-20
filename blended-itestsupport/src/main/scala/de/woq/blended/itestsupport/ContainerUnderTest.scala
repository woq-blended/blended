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

package de.woq.blended.itestsupport

import com.typesafe.config.Config

import scala.collection.convert.Wrappers.JListWrapper

object NamedContainerPort {
  def apply(config : Config) : NamedContainerPort = {
    val privatePort = config.getInt("private")
    val publicPort = if (config.hasPath("public")) 
      config.getInt("public")
    else
      privatePort

    NamedContainerPort(config.getString("name"), privatePort, publicPort)
  }   
}

case class NamedContainerPort(
  name: String, privatePort: Int, publicPort: Int
)

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
  def containerMap(config: Config) = JListWrapper(config.getConfigList("docker.containers")).map { cfg =>
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
      volumes,
      links,
      ports.map { p => (p.name, p) }.toMap
    )
  }
} 

case class ContainerUnderTest(
  ctName          : String,
  imgPattern      : String,
  dockerName      : String,
  volumes         : List[VolumeConfig] = List.empty,
  links           : List[ContainerLink] = List.empty,                             
  ports           : Map[String, NamedContainerPort] = Map.empty
) {
  
  val DEFAULT_PROTOCOL = "tcp"
  
  def url(portName: String, host: String = "127.0.0.1", protocol: String = DEFAULT_PROTOCOL) : Option[String] = 
    ports.get(portName) match {
      case None => None
      case Some(p) => Some(s"$protocol://$host:${p.publicPort}")
    }
}

