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

package de.woq.blended.persistence.internal

import akka.actor._
import akka.event.LoggingReceive
import com.typesafe.config.Config
import de.woq.blended.akka.protocol._
import de.woq.blended.akka.{InitializingActor, MemoryStash, OSGIActor}
import de.woq.blended.container.context.ContainerContext
import de.woq.blended.persistence.protocol._
import org.osgi.framework.BundleContext

import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

object PersistenceManager {

  def apply(impl: PersistenceBackend)(implicit osgiContext : BundleContext) =
    new PersistenceManager(impl) with PersistenceBundleName
}

private[persistence] case class PersistenceManagerBundleState(
  override val config : Config,
  override val bundleContext: BundleContext,
  factories: List[ActorRef] = List.empty
) extends BundleActorState(config, bundleContext)

class PersistenceManager(backend: PersistenceBackend)(implicit osgiContext : BundleContext)
  extends InitializingActor[PersistenceManagerBundleState] with PersistenceBundleName with MemoryStash {

  implicit val logging = context.system.log
  
  override def receive = initializing orElse stashing
  
  override def createState(cfg: Config, bundleContext: BundleContext): PersistenceManagerBundleState = 
    PersistenceManagerBundleState(cfg, bundleContext, List.empty)

  override def becomeWorking(state: PersistenceManagerBundleState): Unit = 
    super.becomeWorking(state)

  override def initialize(state : PersistenceManagerBundleState) : Future[Try[Initialized]] = {
    invokeService(classOf[ContainerContext]) {
      ctx => ctx.getContainerDirectory
    }.mapTo[ServiceResult[String]].map { svcResult =>
      svcResult match {
        case ServiceResult(r) => {
          log.debug(r.toString)
          r match {
            case Some(dir) =>
              backend.initBackend(dir, state.config)
              unstash()
              Success(Initialized(state))
            case _ =>
              log.error(s"No container directory configured")
              Failure(new Exception(s"No container directory configured."))
          }
        }
      }
    }
  }

  def working(state: PersistenceManagerBundleState) = LoggingReceive {
    case RegisterDataFactory(factory: ActorRef) =>
      if (!state.factories.contains(factory)) {
        context.watch(factory)
        becomeWorking(state.copy(factories = factory :: state.factories))
      }
      sender ! DataFactoryRegistered(factory)
    case Terminated(factory) =>
      becomeWorking(state.copy(factories = state.factories.filter(_ != factory)))
    case StoreObject(dataObject) => {
      backend.store(dataObject)
      sender ! QueryResult(List(dataObject))
    }
    case FindObjectByID(uuid, objectType) => {
      backend.get(uuid, objectType) match {
        case None => sender ! QueryResult(List.empty)
        case Some(props) => {
          log.debug(s"Asking [${state.factories.size}] factories to create the dataObject.")
          state.factories.foreach { f => f.forward(CreateObjectFromProperties(props)) }
        }
      }
    }
  }

  override def postStop() : Unit = {
    backend.shutdownBackend()
  }
}
