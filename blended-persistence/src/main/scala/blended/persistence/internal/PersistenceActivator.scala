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

import akka.actor.Props
import blended.akka.{ActorSystemWatching, OSGIActorConfig}
import blended.persistence.PersistenceBackend
import domino.DominoActivator

class PersistenceActivator extends DominoActivator with ActorSystemWatching {

  whenBundleActive {
    whenActorSystemAvailable { cfg =>
      whenServicePresent[PersistenceBackend] { backend =>
        cfg.system.actorOf(Props(PersistenceManager(cfg, backend)))
      }
    }
  }
}
