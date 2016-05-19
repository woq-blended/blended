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

package blended.mgmt.rest.internal

import akka.actor.Props
import blended.akka.ActorSystemWatching
import domino.DominoActivator
import org.osgi.service.http.HttpService
import org.slf4j.LoggerFactory
import blended.updater.remote.RemoteUpdater

class CollectorActivator extends DominoActivator with ActorSystemWatching {

  val log = LoggerFactory.getLogger(classOf[CollectorActivator])

  whenBundleActive {
    whenActorSystemAvailable { cfg =>
      whenServicePresent[RemoteUpdater] { remoteUpdater =>

        // retrieve config as early as possible
        val config = ManagementCollectorConfig(cfg.config, contextPath = "mgmt", remoteUpdater = remoteUpdater)
        log.debug("Config: {}", config)

        whenServicePresent[HttpService] { httpSvc =>
          setupBundleActor(cfg, ManagementCollector.props(cfg = cfg, config))
        }
      }
    }
  }
}

