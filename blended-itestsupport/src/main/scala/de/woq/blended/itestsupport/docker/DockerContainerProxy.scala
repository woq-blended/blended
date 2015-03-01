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

import akka.actor.{PoisonPill, Props, Actor, ActorLogging}
import de.woq.blended.itestsupport.ContainerUnderTest
import de.woq.blended.itestsupport.docker.protocol._

import scala.collection.convert.Wrappers.JListWrapper

class DockerContainerProxy extends Actor with ActorLogging {

  val config = context.system.settings.config
  
  def receive = {
    case StartContainerProxy =>
      
      val external = config.hasPath("docker.external") && config.getBoolean("docker.external")
      log info s"Starting Docker Container Proxy, external Container provider [$external]"
      
      val cuts = JListWrapper(config.getConfigList("docker.containers")).map { cfg => ContainerUnderTest(cfg) }.toList
       
      if (!external) 
        context.actorOf(Props[EmbeddedContainerManager]) ! StartContainerManager(cuts)
      else 
        context.actorOf(Props[ExternalContainerManager]) ! PoisonPill

    case DockerContainerAvailable(result) => result match {
      case Right(cuts) => 
        context.system.eventStream.publish(ContainerProxyStarted(Right(cuts)))
      case Left(e) => 
        log error s"Error initializing Docker Container Proxy [${e.getMessage}]"
        context.stop(self)
    } 
  }
  
}
