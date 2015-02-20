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

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.event.LoggingReceive
import akka.util.Timeout
import com.github.dockerjava.api.model.ExposedPort
import de.woq.blended.itestsupport.docker.protocol._

import scala.concurrent.duration._

object ContainerActor {
  def apply(container: DockerContainer) = new ContainerActor(container)
}

class ContainerActor(container: DockerContainer) extends Actor with ActorLogging {

  case class PerformStart(container: DockerContainer)

  object ContainerStartActor {
    def apply() = new ContainerStartActor
  }

  class ContainerStartActor extends Actor with ActorLogging {

    def receive = LoggingReceive {
      case PerformStart(container) =>
        container.startContainer
        sender ! ContainerStarted(container.containerName)
    }
  }

  implicit val timeout = new Timeout(5.seconds)
  implicit val eCtxt   = context.dispatcher

  var pendingCommands : List[(ActorRef, Any)] = List.empty

  def stopped : Receive = {
    case StartContainer(n) if container.containerName == n  => {
      val starter = context.actorOf(Props(ContainerStartActor()))
      starter ! PerformStart(container)
      context become LoggingReceive(starting(sender()) orElse getPorts )
    }
    case cmd => pendingCommands ::= (sender, cmd)
  }

  def starting(requestor : ActorRef) : Receive = {
    case msg : ContainerStarted =>
      requestor ! msg
      pendingCommands.reverse.map {
        case (requestor: ActorRef, cmd: Any) => self.tell(cmd, requestor)
      }
      context become LoggingReceive(started orElse getPorts)
    case cmd => pendingCommands ::= (sender, cmd)
  }

  def started : Receive = {
    case StopContainer(n) if container.containerName == n  => {
      val requestor = sender
      container.stopContainer
      context become stopped
      requestor ! ContainerStopped(container.containerName)
    }
    case InspectContainer(n) if container.containerName == n => {
      val requestor = sender
      requestor ! container.containerInfo
    }
  }

  def getPorts : Receive = {
    case GetContainerPorts(n) if container.containerName == n => {
      val ports : Map[String, NamedContainerPort] =
        container.ports.mapValues { namedPort =>
          val exposedPort = new ExposedPort(namedPort.privatePort)
          val realPort = exposedPort.getPort
          NamedContainerPort(namedPort.name, realPort, realPort)
        }
      log.debug(s"Sending [${ContainerPorts(ports)}] to [$sender]")
      sender ! ContainerPorts(ports)
    }
  }

  def receive = LoggingReceive(stopped)
}