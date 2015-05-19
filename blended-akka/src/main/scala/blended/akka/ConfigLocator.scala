/*
 * Copyright 2014ff,  https://github.com/woq-blended
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

package blended.akka

import java.io.File

import com.typesafe.config.{Config, ConfigFactory}
import org.slf4j.LoggerFactory

import scala.util.control.NonFatal

class ConfigLocator(configDirectory: String) {

  private[ConfigLocator] val logger = LoggerFactory.getLogger(classOf[ConfigLocator])

  protected def fallbackConfig: Config = ConfigFactory.empty()

  def getConfig(id: String): Config = {
    val file = new File(configDirectory, s"$id.conf")
    logger.debug(s"Retreiving config from [${file.getAbsolutePath}]")

    if (file.exists && file.isFile && file.canRead)
      ConfigFactory.parseFile(file)
    else
      try {
        fallbackConfig.getConfig(id)
      } catch {
        case NonFatal(e) => ConfigFactory.empty()
      }
  }
}


