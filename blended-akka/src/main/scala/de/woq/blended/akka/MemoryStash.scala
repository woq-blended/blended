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

package de.woq.blended.akka

import akka.actor.{ActorLogging, Actor, ActorRef}

trait MemoryStash { this : Actor with ActorLogging =>

  var requests = List.empty[(ActorRef, Any)]

  def stashing : Receive = {
    case msg =>
      log.debug(s"Stashing [${msg}]")
      requests = (sender, msg) :: requests
  }

  def unstash() : Unit = {
    log.debug(s"Unstashing [${requests.size}] messages.")
    requests.reverse.foreach { case (requestor, msg) =>
      self.tell(msg, requestor)
    }
    requests = List.empty[(ActorRef, Any)]
  }
}
