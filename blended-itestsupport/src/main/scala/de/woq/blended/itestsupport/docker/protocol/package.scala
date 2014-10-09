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

package object protocol {

  case object StartContainerManager
  case object StopContainerManager
  case object ContainerManagerStarted
  case object ContainerManagerStopped

  case class StartContainer(name: String)
  case class ContainerStarted(name: String)
  case class DependenciesStarted(container: DockerContainer)

  case class StopContainer(name: String)
  case class ContainerStopped(name: String)

  case class GetContainerPorts(name: String)
  case class ContainerPorts(ports: Map[String, NamedContainerPort])

  case class InspectContainer(name: String)
}
