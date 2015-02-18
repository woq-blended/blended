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

import akka.event.LoggingReceive
import com.typesafe.config.Config
import de.woq.blended.akka.protocol.BundleActorState
import de.woq.blended.akka.{BundleName, InitializingActor}
import de.woq.blended.modules._
import org.osgi.framework.BundleContext

import scala.concurrent.Future
import scala.util.{Success, Try}

object TrackingActor {
  def apply() = new TrackingActor with TrackerBundleName
}

/**
 * Demonstrate how to create an OSGIActor that uses a tracker to monitor service instances
 * it depends on. In the example we are listening to the instance of an JMS ConnectionFactory,
 * which is further qualified with 'activemq' as the provider property. All we do in the
 * example is print out a message when the service instance is added, removed or modified.
 */
class TrackingActor extends InitializingActor[BundleActorState] { this: BundleName =>

  override def receive = LoggingReceive { initializing }
  
  override def createState(cfg: Config, bundleContext: BundleContext) = 
    BundleActorState(cfg, bundleContext)

  override def initialize(state: BundleActorState) : Future[Try[Initialized]] = {
    createTracker(classOf[ConnectionFactory], Some("provider" === "activemq")).map { _ => Success(Initialized(state)) }
  }

  override def working(state: BundleActorState) = LoggingReceive { logging }

  def logging : Receive = {
    case m => log.info(s"$m")
  }
}