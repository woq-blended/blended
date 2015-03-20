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

import akka.actor.Actor
import akka.actor.ActorLogging
import de.woq.blended.akka.MemoryStash
import akka.event.LoggingReceive
import akka.actor.PoisonPill
import de.woq.blended.itestsupport.protocol.TestContextRequest
import akka.camel.CamelExtension
import org.apache.camel.CamelContext
import com.github.dockerjava.api.command.InspectContainerResponse
import akka.actor.Props
import akka.util.Timeout
import scala.concurrent.Future
import de.woq.blended.itestsupport.docker.ContainerManager
import de.woq.blended.itestsupport.docker.DockerClientProvider
import com.github.dockerjava.api.DockerClient
import de.woq.blended.itestsupport.docker.DockerClientFactory
import akka.actor.ActorRef
import de.woq.blended.itestsupport.docker.protocol._

class BlendedTestContextManager extends Actor with ActorLogging with MemoryStash { this : TestContextConfigurator =>
  
  private[this] val camel = CamelExtension(context.system)
  private[this] val config = context.system.settings.config
  
  def initializing : Receive = LoggingReceive {
    case req : TestContextRequest => 
      val containerMgr = context.actorOf(Props(new ContainerManager with DockerClientProvider {
          override def getClient : DockerClient = {
            implicit val logger = context.system.log
            DockerClientFactory(config)
          }
        }))

      containerMgr ! StartContainerManager(req.cuts)
      context.become(starting(sender, containerMgr) orElse stashing)
  } 
  
  def starting(requestor: ActorRef, containerMgr: ActorRef) : Receive = LoggingReceive {
    case ContainerManagerStarted(result) => 
      result match {
        case Right(cuts) => 
          val camelCtxt = configure(cuts, camel.context)
          context.become(working(camelCtxt, containerMgr))
        case m => requestor ! m
      }
  }
  
  def working(testContext: CamelContext, containerMgr: ActorRef) = LoggingReceive {
    case m => log info s"$m"
  } 
    
  def receive = initializing orElse stashing
  
//  private[this] def setupDocker : Unit = {
//    
//  }
//  
//    def containerInfo(cut: ContainerUnderTest) : Future[InspectContainerResponse] = {
//    implicit val eCtxt = system.dispatcher
//    (containerMgr ? InspectContainer(ctName))(new Timeout(3.seconds)).mapTo[InspectContainerResponse]
//  }

//  val dockerProxyProbe = new TestProbe(system)
//
//  def dockerConnect : Unit = {
//    system.eventStream.subscribe(dockerProxyProbe.ref, classOf[ContainerProxyStarted])
//    system.actorOf(Props[DockerContainerProxy]) ! StartContainerProxy
//
//    dockerProxyProbe.expectMsgType[ContainerProxyStarted]
//  }
//
//  dockerConnect

}
