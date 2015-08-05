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

package blended.persistence.internal

import akka.actor._
import akka.event.LoggingReceive
import blended.akka.{MemoryStash, OSGIActor, OSGIActorConfig}
import blended.persistence.PersistenceBackend
import blended.persistence.protocol._

object PersistenceManager {

  def props(cfg: OSGIActorConfig, impl: PersistenceBackend): Props = Props(new PersistenceManager(cfg, impl))
}

class PersistenceManager(cfg: OSGIActorConfig, backend: PersistenceBackend) extends OSGIActor(cfg) with MemoryStash {

  override def receive = working(List.empty)
  
  def working(factories: List[ActorRef]) : Receive = LoggingReceive {
    case RegisterDataFactory(factory: ActorRef) =>
      if (factories.contains(factory)) {
        context.watch(factory)
        context.become(working(factory :: factories))
      }
      sender ! DataFactoryRegistered(factory)
    case Terminated(factory) =>
      context.become( working(factories.filter(_ != factory)) )
    case StoreObject(dataObject) =>
      backend.store(dataObject)
      sender ! QueryResult(List(dataObject))
    case FindObjectByID(uuid, objectType) =>
      backend.get(uuid, objectType) match {
        case None => sender ! QueryResult(List.empty)
        case Some(props) =>
          log.debug(s"Asking [${factories.size}] factories to create the dataObject.")
          factories.foreach { _.forward(CreateObjectFromProperties(props)) }
      }
  }

  override def postStop() : Unit = {
    backend.shutdownBackend()
  }
}
