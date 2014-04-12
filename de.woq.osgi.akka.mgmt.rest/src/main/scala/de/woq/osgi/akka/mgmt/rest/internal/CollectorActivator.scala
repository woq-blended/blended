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

import de.woq.osgi.akka.system.{BundleName, ActorSystemAware}
import org.osgi.framework.BundleActivator
import akka.actor.{ActorSystem, Props}
import org.slf4j.LoggerFactory

class CollectorActivator extends ActorSystemAware with BundleActivator with BundleName {

  val logger = LoggerFactory.getLogger(classOf[CollectorActivator])

  override def bundleSymbolicName = "de.woq.osgi.akka.mgmt.rest"

  def prepareBundleActor(): Props = Props(ManagementCollector(bundleSymbolicName))

  override def postStartActor() {
    bundleContext.findService(classOf[ActorSystem]).andApply { actorSystem =>
      bundleContext.createService(
        new CollectorResource(actorSystem, bundleSymbolicName),
        Map( "servlet-name" -> "woqManagement" )
      )
    }

  }
}
