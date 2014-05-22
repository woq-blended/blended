/*
 * Copyright 2014ff, WoQ - Way of Quality UG(mbH)
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

package de.woq.osgi.akka.persistence.internal

import akka.actor.{ActorSystem, ActorLogging, Actor}
import de.woq.osgi.akka.system.{BundleName, OSGIActor}
import org.osgi.framework.BundleContext
import akka.event.LoggingReceive
import de.woq.osgi.akka.system.protocol.{ServiceResult, Service, ConfigLocatorResponse, InitializeBundle}
import akka.pattern._
import de.woq.osgi.java.container.context.ContainerContext

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
  extends Actor with ActorLogging { this : OSGIActor with BundleName with PersistenceProvider =>

  implicit val logging = log

  def initializing = LoggingReceive {
    case InitializeBundle(_) => {
      val cfg = getActorConfig(bundleSymbolicName)
      val d = invokeService(classOf[ContainerContext]) { ctx => ctx.getContainerDirectory }
      (for {
        config <- cfg
        dir <- d
      } yield (config, dir)) pipeTo(self)
    }
    case (ConfigLocatorResponse(id, config), ServiceResult(Some(dir : String))) if id == bundleSymbolicName => {
      backend.initBackend(dir, config)
      context.become(working)
    }
  }

  def working = LoggingReceive { Actor.emptyBehavior }

  def receive = initializing

  override def postStop() {
    backend.shutdownBackend()
  }
}
