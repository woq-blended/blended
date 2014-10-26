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

package de.woq.blended.samples.tracker.internal

import javax.jms.ConnectionFactory
import javax.management.MBeanServer

import akka.actor.ActorRef
import akka.event.LoggingReceive
import com.typesafe.config.Config
import de.woq.blended.akka.{BundleName, InitializingActor}
import de.woq.blended.modules.toSimpleOpBuilder
import org.osgi.framework.BundleContext

object TrackingActor {
  def apply() = new TrackingActor with TrackerBundleName
}

class TrackingActor extends InitializingActor { this: BundleName =>

  var tracker : Option[ActorRef] = None

  override def receive = LoggingReceive { initializing orElse(logging) }

  override def initialize(config: Config)(implicit bundleContext: BundleContext) : Unit = {
    createTracker(classOf[MBeanServer]).mapTo[ActorRef].map { t =>
      tracker = Some(t)
      self ! Initialized
    }
  }

  override def working = LoggingReceive { logging }

  def logging : Receive = {
    case m => log.info(s"${m}")
  }
}