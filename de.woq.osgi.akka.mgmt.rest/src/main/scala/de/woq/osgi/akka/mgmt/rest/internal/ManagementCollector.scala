/*
 * Copyright 2013, WoQ - Way of Quality UG(mbH)
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

package de.woq.osgi.akka.mgmt.rest.internal

import akka.actor.{ActorLogging, Actor}
import de.woq.osgi.akka.system.BundleName
import de.woq.osgi.java.mgmt_core.ContainerInfo

object ManagementCollector {
  def apply(name : String) = new ManagementCollector with BundleName {
    override def bundleSymbolicName = name
  }
}

class ManagementCollector extends Actor with ActorLogging { this : BundleName =>
  override def receive : Receive = {
    case info : ContainerInfo => log info s"$info"
  }
}
