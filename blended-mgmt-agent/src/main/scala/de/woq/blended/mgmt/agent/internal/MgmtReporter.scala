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

package de.woq.blended.mgmt.agent.internal

import akka.actor.ActorLogging
import akka.event.LoggingReceive
import akka.pattern.pipe
import com.typesafe.config.Config
import de.woq.blended.akka.protocol._
import de.woq.blended.akka.{BundleName, InitializingActor, OSGIActor}
import de.woq.blended.container.id.ContainerIdentifierService
import de.woq.blended.container.registry.protocol._
import org.osgi.framework.BundleContext
import spray.client.pipelining._
import spray.http.HttpRequest
import spray.httpx.SprayJsonSupport

import scala.collection.JavaConversions._
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.Try

object MgmtReporter {
  def apply()(implicit bundleContext: BundleContext) = new MgmtReporter with MgmtAgentBundleName
}

class MgmtReporter extends InitializingActor[BundleActorState] with ActorLogging with SprayJsonSupport { this : OSGIActor with BundleName =>

  case object Tick
  
  override def createState(cfg: Config, bundleContext: BundleContext): BundleActorState = BundleActorState(cfg, bundleContext)

  override def becomeWorking(state: BundleActorState): Unit = {
    context.system.scheduler.schedule(100.milliseconds, 60.seconds, self, Tick)
    super.becomeWorking(state)
  }

  def working(state: BundleActorState) = LoggingReceive {

    case Tick =>
      invokeService[ContainerIdentifierService, ContainerInfo](classOf[ContainerIdentifierService]) { idSvc =>
        new ContainerInfo(idSvc.getUUID, idSvc.getProperties.toMap)
      } pipeTo self

    case ServiceResult(Some(info : ContainerInfo))  =>
      log info s"Performing report [${info.toString}]."

      val pipeline :  HttpRequest => Future[ContainerRegistryResponseOK] = {
        sendReceive ~> unmarshal[ContainerRegistryResponseOK]
      }

      (pipeline{ Post("http://localhost:8181/woq/container", info) }).mapTo[ContainerRegistryResponseOK].pipeTo(self)

    case response : ContainerRegistryResponseOK => log info(s"Reported [${response.id}] to management node")
  }

  def receive = initializing
}
