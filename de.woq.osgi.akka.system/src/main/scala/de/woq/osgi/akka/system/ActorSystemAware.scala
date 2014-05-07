/*
 * Copyright 2014ff, WoQ - Way of Quality UG(mbH)
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

package de.woq.osgi.akka.system

import org.osgi.framework.{BundleActivator, BundleContext}
import akka.actor.{Props, ActorRef, ActorSystem}
import de.woq.osgi.akka.modules._
import org.slf4j.LoggerFactory

case class InitializeBundle(context: BundleContext)

trait BundleName {
  def bundleSymbolicName : String
}

trait ActorSystemAware extends BundleActivator { this : BundleName =>

  private [ActorSystemAware] val log = LoggerFactory.getLogger(classOf[ActorSystemAware])

  var bundleContextRef : BundleContext = _
  var actorRef         : ActorRef = _
  var actorSystemRef   : ActorSystem = _

  def actorSystem   = actorSystemRef
  implicit def bundleContext = bundleContextRef
  def bundleActor   = actorRef

  def prepareBundleActor() : Props

  final def start(osgiBundleContext: BundleContext) {
    this.bundleContextRef = osgiBundleContext

    log debug s"Starting Akka bundle [$bundleSymbolicName]."

    bundleContext.findService(classOf[ActorSystem]) match {
      case Some(svcReference) => svcReference invokeService { system =>
        actorSystemRef = system

        log debug s"Preparing bundle actor for [$bundleSymbolicName]."

        actorRef = system.actorOf(prepareBundleActor(), bundleSymbolicName)
        actorRef ! InitializeBundle(bundleContext)

        postStartBundleActor()
      }
      // TODO : handle this
      case _ =>
    }
  }

  def postStartBundleActor() {}

  final def stop(osgiBundleContext: BundleContext) {

    implicit val bc = osgiBundleContext

    preStopBundleActor()
    actorSystem.stop(bundleActor)
  }

  def preStopBundleActor() {}

}
