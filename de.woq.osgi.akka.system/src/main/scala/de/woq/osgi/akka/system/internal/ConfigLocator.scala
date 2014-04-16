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
import com.typesafe.config.{ConfigException, ConfigFactory}
import java.io.File

trait ConfigDirectoryProvider {
  def configDirectory : String
}

class ConfigLocator extends Actor with ActorLogging { this: ConfigDirectoryProvider =>

  case object Initialize

  override def preStart() { self ! Initialize }

  def receive = initializing

  def initializing : Receive = {
    case Initialize => {
      log info s"Initializing ConfigLocator with directory [${configDirectory}]."
      context.become(working)
    }
  }

  def working: Actor.Receive = {

    case ConfigLocatorRequest(id) => {
      
      val file = new File(configDirectory, s"$id.conf")

      val config =
        if (file.exists && file.isFile && file.canRead)
          ConfigFactory.parseFile(file)
        else
          try
            context.system.settings.config.getConfig(id)
          catch {
            case me : ConfigException.Missing  => ConfigFactory.empty()
          }

      sender ! ConfigLocatorResponse(id, config)
    }
  }
}

object ConfigLocator {
  def apply(configDir : String) = new ConfigLocator with ConfigDirectoryProvider {
    override def configDirectory = configDir
  }
}
