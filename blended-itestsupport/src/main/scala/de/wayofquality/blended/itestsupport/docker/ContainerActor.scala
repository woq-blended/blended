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

package de.wayofquality.blended.itestsupport.docker

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.event.LoggingReceive
import akka.util.Timeout
import com.github.dockerjava.api.model.ExposedPort
import de.wayofquality.blended.itestsupport.{ContainerUnderTest, NamedContainerPort}
import de.wayofquality.blended.itestsupport.docker.protocol._
import scala.concurrent.duration._
import com.github.dockerjava.api.DockerClient

object ContainerActor {
  def apply(container: ContainerUnderTest)(implicit client: DockerClient) = new ContainerActor(container)
}

class ContainerActor(container: ContainerUnderTest)(implicit client: DockerClient) extends Actor with ActorLogging {

  case object PerformStart

  object ContainerStartActor {
    def apply(cut: ContainerUnderTest) = new ContainerStartActor(cut)
  }

  class ContainerStartActor(cut: ContainerUnderTest) extends Actor with ActorLogging {

    def receive = LoggingReceive {
      case PerformStart =>
        val dc = new DockerContainer(cut)
        dc.startContainer
        sender ! ContainerStarted(Right(cut.ctName))
    }
  }

  implicit val timeout = new Timeout(5.seconds)
  implicit val eCtxt   = context.dispatcher

  def stopped : Receive = LoggingReceive {
    case StartContainer(n) if container.ctName == n  => {
      val starter = context.actorOf(Props(ContainerStartActor(container)))
      starter ! PerformStart
      context.become(starting(sender, container))
    }
  }

  def starting(requestor : ActorRef, cut: ContainerUnderTest) : Receive = LoggingReceive {
    case msg : ContainerStarted =>
      requestor ! msg
      context become LoggingReceive(started(cut))
  }

  def started(cut: ContainerUnderTest) : Receive = LoggingReceive {
    case StopContainer => {
      val requestor = sender
      new DockerContainer(cut).stopContainer
      context become stopped
      requestor ! ContainerStopped(Right(container.ctName))
    }
  }

  def receive = stopped
}