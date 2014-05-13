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

package de.woq.osgi.akka.system.internal

import akka.actor.{Stash, ActorLogging, Actor}
import de.woq.osgi.akka.system.protocol._
import com.typesafe.config.{ConfigException, ConfigFactory}
import java.io.File
import akka.event.LoggingReceive

trait ConfigDirectoryProvider {
  def configDirectory : String
}

object ConfigLocator {
  def apply(configDir : String) = new ConfigLocator with ConfigDirectoryProvider {
    override def configDirectory = configDir
  }
}

class ConfigLocator extends Actor with ActorLogging with Stash { this: ConfigDirectoryProvider =>

  case object Initialize

  override def preStart() { self ! Initialize }

  def receive = initializing

  def initializing : Receive = LoggingReceive {
    case Initialize => {
      log info s"Initializing ConfigLocator with directory [${configDirectory}]."
      unstashAll()
      context.become(working)
    }
    case _ => stash()
  }

  def working: Actor.Receive = LoggingReceive {

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


