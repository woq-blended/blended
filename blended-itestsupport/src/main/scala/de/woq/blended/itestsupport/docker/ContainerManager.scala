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
import com.github.dockerjava.api.model.Container
import de.woq.blended.itestsupport.ContainerUnderTest
import scala.concurrent.Future
import scala.concurrent.duration._
import akka.actor._
import akka.pattern.ask
import akka.event.{LoggingAdapter, LoggingReceive}
import akka.util.Timeout
import com.typesafe.config.Config
import de.woq.blended.itestsupport.docker.protocol._
import scala.collection.convert.Wrappers.JListWrapper
import de.woq.blended.itestsupport.NamedContainerPort

private[docker] case class InternalMapDockerContainers(requestor: ActorRef, cuts: Map[String, ContainerUnderTest], client: DockerClient)
private[docker] case class InternalDockerContainersMapped(requestor: ActorRef, result : DockerResult[Map[String, ContainerUnderTest]])
private[docker] case class InternalStartContainers(requestor: ActorRef, cuts : Map[String, ContainerUnderTest])
private[docker] case class InternalContainersStarted(requestor: ActorRef, result : DockerResult[Map[String, ContainerUnderTest]])

trait DockerClientProvider {
  def getClient : DockerClient
}

class ContainerManager extends Actor with ActorLogging with Docker with VolumeBaseDir { this:  DockerClientProvider =>

  implicit val eCtxt   = context.dispatcher
  val client : DockerClient = getClient  

  override val config: Config = context.system.settings.config
  override val logger: LoggingAdapter = context.system.log
  
  def mapper = context.actorOf(Props(new DockerContainerMapper))
  def starter = context.actorOf(Props(new DockerContainerStarter))
  
  def receive = LoggingReceive {
    
    case StartContainerManager(containers) => 
      val externalCt = config.getBoolean("docker.external")
      log.info(s"Containers have been started externally: [$externalCt]")

      externalCt match {
        case true => mapper ! InternalMapDockerContainers(sender, containers, client)
        case _ => starter ! InternalStartContainers(sender, containers)
      }
      
    case r : InternalContainersStarted => r.result match {
      case Right(cuts) => mapper ! InternalMapDockerContainers(r.requestor, cuts, client)
      case _ => r.requestor ! r.result
    }  

    case r : InternalDockerContainersMapped => 
      log.info(s"Container Manager started with docker attached docker containers: [${r.result}]")
      r.requestor ! ContainerManagerStarted(r.result)
    
  }
  
  def starting(requestor: ActorRef, cuts: Map[String, ContainerUnderTest]) = Actor.emptyBehavior
  
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

}


//class EmbeddedContainerManager extends ContainerManager with DockerClientProvider {
//  override def getClient : DockerClient = {
//    implicit val logger = context.system.log
//    DockerClientFactory(context.system.settings.config)
//  }
//  
//  def receive = Actor.emptyBehavior
//
//  def starting(
//    requestor           : ActorRef,
//    pendingContainers   : List[(ActorRef, ContainerUnderTest)],
//    startingContainers  : List[ContainerUnderTest],
//    runningContainers   : List[ContainerUnderTest]
//  ) : Receive = LoggingReceive {
//    case ContainerStarted(result) => result match {
//      case Right(name) =>
//        pendingContainers.foreach { _._1 ! ContainerStarted(Right(name)) }
//        val startedCt = startingContainers.filter(_.ctName == name).head
//        val remaining = startingContainers.filter(_ != startedCt)
//        val started = startedCt :: runningContainers
//
//        if (pendingContainers.isEmpty && remaining.isEmpty) {
//          log.info(s"Container Manager started [$started]")
//          context.become(running(started))
//          requestor ! DockerContainerAvailable(Right(started))
//        } else {
//          context.become(starting(requestor, pendingContainers, remaining, started))
//        }
//      case Left(e) => 
//        log error s"Errot in starting docker containers [${e.getMessage}]"
//        requestor ! Left(e)
//        context.stop(self)
//    }
//    case DependenciesStarted(result) => result match {
//      case Right(ct) =>
//        val pending  = pendingContainers.filter(_._1 != sender())
//        val ctActor  = context.actorOf(Props(ContainerActor(ct)), ct.containerName)
//        ctActor ! StartContainer(ct.containerName)
//        val cut = pendingContainers.filter(_._1 == sender()).head._2
//        context.become(starting(requestor, pending, cut :: startingContainers, runningContainers))
//      case Left(e) => 
//        requestor ! DockerContainerAvailable(Left(e))
//        context.stop(self)
//    } 
//  }
//
//  def running(runningContainers: List[ContainerUnderTest]) : Receive = LoggingReceive { Actor.emptyBehavior }
//
//  def receive : Receive = {
//    case StartContainerManager(containers) =>
//      log info s"Initializing Container manager with [$containers]"
//
//      val cuts   = configureDockerContainer(containers)
//
//      val noDeps   = cuts.filter( _.links.isEmpty)
//      val withDeps = cuts.filter( _.links.nonEmpty)
//      val pending  = withDeps.map { cut =>
//        ( context.actorOf(Props(DependentContainerActor(cut.dockerContainer.get))), cut )
//      }
//
//      log.info(s"$noDeps")
//      log.info(s"$withDeps")
//
//      noDeps.foreach{ startContainer }
//
//      context.become(starting(sender, pending, noDeps, List.empty))
//  }
//  
//  private[this] def configureDockerContainer(cut : List[ContainerUnderTest]) : List[ContainerUnderTest] = {
//    cut.map { ct =>
//      search(searchByTag(ct.imgPattern)).zipWithIndex.map { case (img, idx) =>
//        val ctName = s"${ct.ctName}_$idx"
//
//        val dc = new DockerContainer(img.getId, ctName)
//
//        ct.links.foreach { cl => dc.withLink(s"${cl.container}:${cl.hostname}")}
//        dc.withNamedPorts(ct.ports.values.toSeq)
//
//        ct.copy(ctName = ctName, dockerContainer = Some(dc))
//      }
//    }.flatten
//  }
//  
//  private[this] def startContainer(cut : ContainerUnderTest) : Unit = {
//
//    val actor = context.actorOf(Props(ContainerActor(cut.dockerContainer.get)), cut.ctName)
//    actor ! StartContainer(cut.ctName)
//
//  }
//}
//

class DockerContainerMapper extends Actor with ActorLogging {
   
  def receive = LoggingReceive {
    case InternalMapDockerContainers(requestor, cuts, client) => 
      log.debug(s"Mapping docker containers $cuts")
      
      val mapped : Map[String, Either[Throwable, ContainerUnderTest]] = cuts.map { case (name, cut) =>
        dockerContainer(cut, client) match {
          case e if e.isEmpty => (name, Left(new Exception(s"No suitable docker container found for [${cut.ctName}]")))
          case head :: rest if rest.isEmpty => (name, Right(mapDockerContainer(head, cut)))
          case _ => (name, Left(new Exception(s"No unique docker container found for [${cut.ctName}]")))
        }
      }
      
      val errors = mapped.values.filter(_.isLeft).map(_.left.get.getMessage)
      val mappedCuts = mapped.values.filter(_.isRight).map(_.right.get)
      
      val result = errors match {
        case e if e.isEmpty => InternalDockerContainersMapped(requestor, Right(mappedCuts.map { c => (c.ctName, c) }.toMap ))
        case l => InternalDockerContainersMapped(requestor, Left(new Exception(errors.mkString(","))))
      }
      
      log.debug(s"$result")
      sender ! result
  }
  
  private[docker] def mapDockerContainer(dc: Container, cut: ContainerUnderTest) : ContainerUnderTest = {
    val mapped = cut.ports.map { case (name, port) => 
      (name, mapPort(dc.getPorts, port)) 
    }
    
    cut.copy(dockerName = rootName(dc), ports = mapped)    
  }
  
  private[docker] def rootName(dc : Container) : String = 
    dc.getNames.filter { _.indexOf("/", 1) == -1 }.head.substring(1)
  
    
  private[docker] def mapPort(dockerPorts: Array[Container.Port], port: NamedContainerPort) : NamedContainerPort = {
    dockerPorts.filter { _.getPrivatePort() == port.privatePort }.toList match {
      case e if e.isEmpty => port
      case l => port.copy(publicPort = l.head.getPublicPort)
    }
  }
  
  private[docker] def dockerContainer(cut: ContainerUnderTest, client: DockerClient) : List[Container] = {
    
    val dc = JListWrapper(client.listContainersCmd().exec()).toList
    
    dockerContainerByName(cut, dc) match {
      case e if e.isEmpty => dockerContainerByImage(cut, dc)
      case l => l
    }
  } 
  
  private[docker] def dockerContainerByName(cut: ContainerUnderTest, dc: List[Container]) : List[Container] = {
    log.debug(s"Matching Docker Container by name: [${cut.dockerName}]")
    dc.filter(_.getNames.contains(s"/${cut.dockerName}"))
  }

  private[docker] def dockerContainerByImage(cut: ContainerUnderTest, dc: List[Container]) : List[Container] = {
    log.debug(s"Matching Docker Container by Image: [${cut.imgPattern}]")
    dc.filter(_.getImage.matches(cut.imgPattern))
  }
}

class DockerContainerStarter extends Actor with ActorLogging {
  
  def receive = LoggingReceive {
    case InternalStartContainers(requestor, cuts) =>
      log.info(s"Starting docker containers $cuts")
      sender ! InternalContainersStarted(requestor, Right(cuts))
  }
}
