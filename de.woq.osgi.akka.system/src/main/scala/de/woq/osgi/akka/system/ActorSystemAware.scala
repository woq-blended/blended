/*
 * Copyright 2014, WoQ - Way of Quality UG(mbH)
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
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

import org.osgi.framework.{BundleActivator, BundleContext, ServiceReference}
import akka.actor.{Props, ActorRef, ActorSystem}

case class InitializeBundle(context: BundleContext)

trait ActorSystemAware { this : BundleActivator =>

  var bundleContextRef : BundleContext = null
  var systemServiceRef : ServiceReference[ActorSystem] = null
  var actorSystemRef   : ActorSystem = null
  var actorRef         : ActorRef = null;

  def bundleContext : BundleContext = bundleContextRef

  def actorSystem : ActorSystem = actorSystemRef

  def bundleId : String

  def prepareBundleActor() : Props

  final def start(context: BundleContext) {
    this.bundleContextRef = context
    systemServiceRef = bundleContext.getServiceReference(classOf[ActorSystem])

    if (systemServiceRef != null) {
      actorSystemRef = bundleContext.getService[ActorSystem](systemServiceRef)
    }

    actorRef = actorSystem.actorOf(prepareBundleActor(), bundleId)
    actorRef ! InitializeBundle(context)
  }

  final def stop(context: BundleContext) {
    if (actorRef != null) {
      actorSystem.stop(actorRef)
    }

    if (systemServiceRef != null) {
      context.ungetService(systemServiceRef)
    }
  }
}
