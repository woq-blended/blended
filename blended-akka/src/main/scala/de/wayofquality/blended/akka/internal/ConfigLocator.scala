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

package de.wayofquality.blended.akka.internal

import java.io.File
import akka.actor.{Actor, ActorLogging}
import akka.event.LoggingReceive
import com.typesafe.config.{ConfigException, ConfigFactory}
import de.wayofquality.blended.akka.MemoryStash
import de.wayofquality.blended.akka.protocol._
import com.typesafe.config.Config
import org.slf4j.LoggerFactory

trait ConfigDirectoryProvider {
  def configDirectory : String
}

trait ConfigLocator { this: ConfigDirectoryProvider =>
  
  private[ConfigLocator] val logger = LoggerFactory.getLogger(classOf[ConfigLocator])
  
  def fallback : Option[Config]

  def getConfig(id: String) : Config = {
      val file = new File(configDirectory, s"$id.conf")
      logger.debug(s"Retreiving config from [${file.getAbsolutePath}]")

      if (file.exists && file.isFile && file.canRead)
        ConfigFactory.parseFile(file)
      else
        fallback match {
          case Some(cfg) => try {
            cfg.getConfig(id)
          } catch {
            case t: Throwable => ConfigFactory.empty()
          }
          case _ => ConfigFactory.empty()
        }
  }
}


