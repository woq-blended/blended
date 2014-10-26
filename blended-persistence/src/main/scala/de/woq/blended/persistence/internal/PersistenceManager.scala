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

trait PersistenceProvider {
  val backend : PersistenceBackend
}

object PersistenceManager {

  def apply(impl: PersistenceBackend)(implicit osgiContext : BundleContext) =
    new PersistenceManager() with OSGIActor with PersistenceBundleName with PersistenceProvider {
      override val backend = impl
    }
}

class PersistenceManager()(implicit osgiContext : BundleContext)
  extends InitializingActor with PersistenceBundleName with MemoryStash { this : OSGIActor with PersistenceProvider =>

  implicit val logging = context.system.log
  private var factories : List[ActorRef] = List.empty

  override def receive = initializing orElse(stashing)

  override def initialize(config: Config)(implicit bundleContext: BundleContext) : Unit = {
    invokeService(classOf[ContainerContext]) {
      ctx => ctx.getContainerDirectory
    }.mapTo[ServiceResult[String]].map { svcResult =>
      log.info(svcResult.toString)
      svcResult match {
        case ServiceResult(r) => {
          log.debug(r.toString)
          r match {
            case Some(dir) =>
              backend.initBackend(dir, config)
              self ! Initialized
              unstash()
            case _ =>
              log.error(s"No container directory configured")
              context.stop(self)
          }
        }
      }
    }
  }

  def working = LoggingReceive {
    case RegisterDataFactory(factory: ActorRef) => {
      if (!factories.contains(factory)) {
        factories = factory :: factories
        context.watch(factory)
      }
      sender ! DataFactoryRegistered(factory)
    }
    case Terminated(factory) => {
      factories = factories.filter(_ != factory)
    }
    case StoreObject(dataObject) => {
      backend.store(dataObject)
      sender ! QueryResult(List(dataObject))
    }
    case FindObjectByID(uuid, objectType) => {
      backend.get(uuid, objectType) match {
        case None => sender ! QueryResult(List.empty)
        case Some(props) => {
          log.debug(s"Asking [${factories.size}] factories to create the dataObject.")
          factories.foreach { f => f.forward(CreateObjectFromProperties(props)) }
        }
      }
    }
  }

  override def postStop() {
    backend.shutdownBackend()
  }
}
