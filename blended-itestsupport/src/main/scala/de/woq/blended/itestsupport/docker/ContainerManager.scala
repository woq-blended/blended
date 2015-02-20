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

import com.github.dockerjava.api.DockerClient

import scala.concurrent.Future
import scala.concurrent.duration._
import akka.actor._
import akka.pattern.ask
import akka.event.{LoggingAdapter, LoggingReceive}
import akka.util.Timeout
import com.typesafe.config.Config
import de.woq.blended.itestsupport.docker.protocol._

trait DockerClientProvider {
  def getClient : DockerClient
}

class ContainerManager extends Actor with ActorLogging with Docker with VolumeBaseDir { this:  DockerClientProvider =>

  implicit val timeout = Timeout(30.seconds)
  implicit val eCtxt   = context.dispatcher
  implicit val client  = getClient

  override val config: Config = context.system.settings.config
  override val logger: LoggingAdapter = context.system.log

  var pendingContainer : Map [String, ActorRef] = Map.empty
  var runningContainer : Map [String, ActorRef] = Map.empty
  var requestor : Option[ActorRef] = _

  def starting : Receive = LoggingReceive {
    case StartContainerManager => {
      log info s"Initializing Container manager"

      requestor = Some(sender)
      configuredContainers.foreach{ case(name, ct) =>
        if (ct.links.isEmpty) {
          val actor = context.actorOf(Props(ContainerActor(ct)), name)
          actor ! StartContainer(name)
        } else {
          val actor = context.actorOf(Props(DependentContainerActor(ct)))
          pendingContainer += (ct.containerName -> actor)
        }
      }
      if (checkPending) context become running
    }
    case DependenciesStarted(ct) => {
      pendingContainer -= ct.containerName
      val actor = context.actorOf(Props(ContainerActor(ct)), ct.containerName)
      actor ! StartContainer(ct.containerName)
    }
    case ContainerStarted(name) => {
      runningContainer += (name -> sender)
      pendingContainer.values.foreach { _ ! ContainerStarted(name) }
      if (checkPending) context.become(running)
    }
  }

  def running : Receive = LoggingReceive {
    case ContainerStarted(name) => {
      runningContainer += (name -> sender)
    }
    case GetContainerPorts(name) => {
      val requestor = sender
      containerActor(name).mapTo[ActorRef].onSuccess { case ct =>
        ct.tell(GetContainerPorts(name), requestor)
      }
    }
    case InspectContainer(name) => {
      val requestor = sender
      containerActor(name).mapTo[ActorRef].onSuccess{ case ct =>
        ct.tell(InspectContainer(name), requestor)
      }
    }
    case StopContainerManager => {
      val requestor = sender

      log debug s"Stopping container [${runningContainer}]"

      val stopFutures = runningContainer.collect {
        case (name, ctActor) => (ctActor ? StopContainer(name)).mapTo[ContainerStopped]
      }

      val stopped = Future.sequence(stopFutures).map( _ => requestor ! ContainerManagerStopped )

      context stop(self)
    }
  }

  def receive = starting

  private def containerActor(name: String) = context.actorSelection(name).resolveOne()

  private def checkPending = {
    if (pendingContainer.isEmpty) {
      log info "Container Manager started."
      requestor.foreach { _ ! ContainerManagerStarted }
      true
    } else false
  }
}
