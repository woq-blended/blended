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
import de.woq.blended.itestsupport.ContainerUnderTest

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

  def starting(
    requestor           : ActorRef,              
    pendingContainers   : List[(ActorRef, ContainerUnderTest)],
    startingContainers  : List[ContainerUnderTest],
    runningContainers   : List[ContainerUnderTest]
  ) : Receive = LoggingReceive {
    case ContainerStarted(name) =>
      pendingContainers.foreach { _._1 ! ContainerStarted(name) }
      val startedCt = startingContainers.filter(_.ctName == name).head
      val remaining = startingContainers.filter(_ != startedCt)
      val started = startedCt :: runningContainers

      if (pendingContainers.isEmpty && remaining.isEmpty) {
        log.info(s"Container Manager started [$started]")
        context.become(running(started))
        requestor ! ContainerManagerStarted(started)
      } else {
        context.become(starting(requestor, pendingContainers, remaining, started))
      }
    case DependenciesStarted(ct) =>
      val pending  = pendingContainers.filter(_._1 != sender())
      val ctActor  = context.actorOf(Props(ContainerActor(ct)), ct.containerName)
      ctActor ! StartContainer(ct.containerName)
      val cut = pendingContainers.filter(_._1 == sender()).head._2
      context.become(starting(requestor, pending, cut :: startingContainers, runningContainers))
  }
  
  def running(runningContainers: List[ContainerUnderTest]) : Receive = LoggingReceive { Actor.emptyBehavior }
  
//  def running(runningContainers : List[Cont : Receive = LoggingReceive {
//    case ContainerStarted(name) => {
//      runningContainer += (name -> sender)
//    }
//    case GetContainerPorts(name) => {
//      val requestor = sender
//      containerActor(name).mapTo[ActorRef].onSuccess { case ct =>
//        ct.tell(GetContainerPorts(name), requestor)
//      }
//    }
//    case InspectContainer(name) => {
//      val requestor = sender
//      containerActor(name).mapTo[ActorRef].onSuccess{ case ct =>
//        ct.tell(InspectContainer(name), requestor)
//      }
//    }
//    case StopContainerManager => {
//      val requestor = sender
//
//      log debug s"Stopping container [${runningContainer}]"
//
//      val stopFutures = runningContainer.collect {
//        case (name, ctActor) => (ctActor ? StopContainer(name)).mapTo[ContainerStopped]
//      }
//
//      val stopped = Future.sequence(stopFutures).map( _ => requestor ! ContainerManagerStopped )
//
//      context stop(self)
//    }
//  }

  def receive : Receive = {
    case StartContainerManager(containers) => 
      log info s"Initializing Container manager with [$containers]"

      val cuts   = configureDockerContainer(containers)

      val noDeps   = cuts.filter( _.links.isEmpty)
      val withDeps = cuts.filter( _.links.nonEmpty)
      val pending  = withDeps.map { cut =>
        ( context.actorOf(Props(DependentContainerActor(cut.dockerContainer.get))), cut )
      }

      log.info(s"$noDeps")
      log.info(s"$withDeps")

      noDeps.foreach{ cut =>
        val actor = context.actorOf(Props(ContainerActor(cut.dockerContainer.get)))
        actor ! StartContainer(cut.ctName)
      }

      context.become(starting(sender, pending, noDeps, List.empty))
  }
  
  private[this] def configureDockerContainer(cut : List[ContainerUnderTest]) : List[ContainerUnderTest] = {
    cut.map { ct => 
      search(searchByTag(ct.imgPattern)).zipWithIndex.map { case (img, idx) =>
        val ctName = s"${ct.ctName}_$idx"
        ct.copy(ctName = ctName, dockerContainer = Some(new DockerContainer(img.getId, ctName)))
      }
    }.flatten
  }

  private[this] def containerActor(name: String) = context.actorSelection(name).resolveOne().mapTo[ActorRef]

}
