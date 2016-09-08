/*
 * Copyright 2014ff,  https://github.com/woq-blended
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

package blended.itestsupport

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.camel.CamelExtension
import akka.event.LoggingReceive
import akka.pattern._
import com.github.dockerjava.api.DockerClient
import blended.akka.MemoryStash
import blended.itestsupport.condition.{Condition, ConditionActor, ConditionProvider}
import blended.itestsupport.docker.{ContainerManager, DockerClientFactory, DockerClientProvider}
import blended.itestsupport.docker.protocol._
import blended.itestsupport.protocol.{TestContextRequest, _}
import org.apache.camel.CamelContext

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
      context.become(starting(List(sender()), containerMgr) orElse stashing)
  } 
  
  def starting(requestors: List[ActorRef], containerMgr: ActorRef) : Receive = LoggingReceive {
    case ContainerManagerStarted(result) => 
      result match {
        case Right(cuts) => 
          val camelCtxt = configure(cuts, camel.context)
          context.become(working(cuts, camelCtxt, containerMgr))
          requestors.foreach(_ ! camelCtxt)
        case m => requestors.foreach(_ ! m)
      }
    case req : TestContextRequest => context.become(starting(sender :: requestors, containerMgr))
  }
  
  def working(cuts: Map[String, ContainerUnderTest], testContext: CamelContext, containerMgr: ActorRef) = LoggingReceive {
    case req : TestContextRequest => sender ! testContext
    
    case ContainerReady_? => 
      implicit val eCtxt = context.system.dispatcher

      val condition = containerReady(cuts)
      
      log.info(s"Waiting for container condition(s) [$condition}]")
      
      val checker = context.system.actorOf(Props(ConditionActor(condition)))

      (checker ? CheckCondition)(condition.timeout).map {
        case cr: ConditionCheckResult => ContainerReady(cr.allSatisfied)
        case _ => ContainerReady(false)
      }.pipeTo(sender())

    case ConfiguredContainers_? => sender ! ConfiguredContainers(cuts)
      
    case scm : StopContainerManager =>
      camel.context.stop()
      containerMgr.forward(scm)
  } 
   
  def containerReady(cuts: Map[String, ContainerUnderTest]) : Condition = ConditionProvider.alwaysTrue()

  def receive = initializing orElse stashing
  
}
