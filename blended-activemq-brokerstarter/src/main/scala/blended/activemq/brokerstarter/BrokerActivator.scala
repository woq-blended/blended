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

package blended.activemq.brokerstarter

import akka.actor.{ActorSystem, PoisonPill, Props}
import blended.activemq.brokerstarter.internal.{BrokerControlActor, StartBroker, StopBroker}
import blended.akka.OSGIActorConfig
import blended.domino.TypesafeConfigWatching
import domino.DominoActivator
import org.slf4j.LoggerFactory

class BrokerActivator extends DominoActivator
  with TypesafeConfigWatching {

  whenBundleActive {
    val log = LoggerFactory.getLogger(classOf[BrokerActivator])

    whenServicePresent[ActorSystem] { actorSys =>

      val actor = actorSys.actorOf(Props[BrokerControlActor], bundleContext.getBundle().getSymbolicName())

      onStop {
        actor ! PoisonPill
      }

      whenTypesafeConfigAvailable { (cfg, idSvc) =>
        actor ! StartBroker(OSGIActorConfig(bundleContext, actorSys, cfg, idSvc))

        onStop {
          actor ! StopBroker
        }
      }
    }
  }
}
