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

package de.woq.osgi.akka.system.internal

import akka.osgi.ActorSystemActivator
import org.osgi.framework.BundleContext
import akka.actor.{Props, ActorSystem}
import akka.event.{LogSource, Logging}

class WoQActivator extends ActorSystemActivator {

  def configure(context: BundleContext, system: ActorSystem) {
    val log = Logging(system, this)

    system.actorOf(Props(ConfigLocator()), "ConfigLocator")

    registerService(context, system)
    log.info("ActorSystem [" + system.name + "] initialized." )
  }

  override def getActorSystemName(context: BundleContext): String = "WoQActorSystem"
}

object WoQActivator {
  implicit val logSource: LogSource[AnyRef] = new LogSource[AnyRef] {
    def genString(o: AnyRef): String = o.getClass.getName
    override def getClazz(o: AnyRef): Class[_] = o.getClass
  }
}