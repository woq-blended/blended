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

import akka.actor.{ActorLogging, Actor}
import de.woq.osgi.akka.system.{ConfigLocatorResponse, ConfigLocatorRequest}
import com.typesafe.config.{Config, ConfigFactory}
import java.io.File
import scala.concurrent.Future

trait ConfigDirectoryProvider {
  def getConfigDiectory : String
}

trait KarafConfigDirectoryProvider extends ConfigDirectoryProvider {
  override def getConfigDiectory = System.getProperty("karaf.home") + "/etc"
}

class ConfigLocator extends Actor with ActorLogging { this: ConfigDirectoryProvider =>

  def receive: Actor.Receive = {

    case ConfigLocatorRequest(id) =>
      val config =
        try {
          ConfigFactory.parseFile(new File(getConfigDiectory, id + ".conf"))
        } catch {
          case _ : Throwable => context.system.settings.config.getConfig(id)
        }

      sender ! ConfigLocatorResponse(id, config)
  }
}

object ConfigLocator {
  def apply() = new ConfigLocator with KarafConfigDirectoryProvider
}
