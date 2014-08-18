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

import akka.actor.{ActorRef, ActorSystem, PoisonPill, Props}
import de.woq.blended.modules._
import de.woq.blended.akka.protocol._
import org.osgi.framework.{BundleActivator, BundleContext}

trait BundleName {
  def bundleSymbolicName : String
}

trait ActorSystemAware extends BundleActivator { this : BundleName =>

  var bundleContextRef : BundleContext = _
  var actorRef         : ActorRef = _

  implicit def bundleContext = bundleContextRef
  def bundleActor   = actorRef

  def prepareBundleActor() : Props

  final def start(osgiBundleContext: BundleContext) {
    this.bundleContextRef = osgiBundleContext

    bundleContext.findService(classOf[ActorSystem]) match {
      case Some(svcReference) => svcReference invokeService { system =>
        system.log debug s"Preparing bundle actor for [$bundleSymbolicName]."

        actorRef = system.actorOf(prepareBundleActor(), bundleSymbolicName)
        actorRef ! InitializeBundle(bundleContext)

        system.eventStream.publish(BundleActorStarted(bundleSymbolicName))
        postStartBundleActor()
      }
      // TODO : handle this
      case _ =>
    }
  }

  def postStartBundleActor() {

  }

  final def stop(osgiBundleContext: BundleContext) {

    implicit val bc = osgiBundleContext

    bundleActor match {
      case a : ActorRef => {
        preStopBundleActor()
        bundleActor ! PoisonPill
      }
      case _ =>
    }
  }

  def preStopBundleActor() {}
}
