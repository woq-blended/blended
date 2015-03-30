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
import de.woq.blended.itestsupport.condition.ConditionProvider
import de.woq.blended.itestsupport.condition.Condition
import de.woq.blended.itestsupport.protocol._
import de.woq.blended.itestsupport.condition.ConditionActor
import scala.concurrent.Await
import akka.pattern._

class BlendedTestContextManager extends Actor with ActorLogging with MemoryStash { this : TestContextConfigurator =>
  
  val camel = CamelExtension(context.system)
  
  def initializing : Receive = LoggingReceive {
    case req : TestContextRequest => 
      log.debug("Configuring Camel Extension for the test...")
      val containerMgr = context.actorOf(Props(new ContainerManager with DockerClientProvider {
          override def getClient : DockerClient = {
            implicit val logger = context.system.log
            DockerClientFactory(context.system.settings.config)
          }
        }), "ContainerMgr")

      containerMgr ! StartContainerManager(req.cuts)
      context.become(starting(sender, containerMgr) orElse stashing)
  } 
  
  def starting(requestor: ActorRef, containerMgr: ActorRef) : Receive = LoggingReceive {
    case ContainerManagerStarted(result) => 
      result match {
        case Right(cuts) => 
          val camelCtxt = configure(cuts, camel.context)
          context.become(working(cuts, camelCtxt, containerMgr))
          requestor ! camelCtxt
        case m => requestor ! m
      }
  }
  
  def working(cuts: Map[String, ContainerUnderTest], testContext: CamelContext, containerMgr: ActorRef) = LoggingReceive {
    case ContainerReady_? => 
      implicit val eCtxt = context.system.dispatcher

      val condition = containerReady(cuts)
      
      val checker = context.system.actorOf(Props(ConditionActor(condition)))

      ((checker ? CheckCondition)(condition.timeout).map { result =>
        result match {
          case cr: ConditionCheckResult => ContainerReady(cr.allSatisfied)
          case _ => ContainerReady(false)
        }
      }).pipeTo(sender)
      
    case StopContainerManager => 
      camel.context.stop()
      containerMgr forward(StopContainerManager)
  } 
   
  def containerReady(cuts: Map[String, ContainerUnderTest]) : Condition = ConditionProvider.alwaysTrue()

  def receive = initializing orElse stashing
  
}
