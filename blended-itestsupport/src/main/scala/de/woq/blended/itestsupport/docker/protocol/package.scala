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

import de.woq.blended.itestsupport.{ContainerUnderTest, NamedContainerPort}

package object protocol {

  type DockerResult[T] = Either[Exception, T]
  
  case class StartContainerManager(containerUnderTest : Map[String, ContainerUnderTest])
  case object StopContainerManager
  
  case class ContainerManagerStarted(containerUnderTest : DockerResult[Map[String, ContainerUnderTest]])
  case object ContainerManagerStopped

  case class StartContainer(name: String)
  case class ContainerStarted(name: DockerResult[String])
  case class DependenciesStarted(container: DockerResult[ContainerUnderTest])

  case class StopContainer(name: String)
  case class ContainerStopped(name: DockerResult[String])

  case class InspectContainer(name: String)
}
