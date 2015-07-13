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

package blended.container.context.internal

import blended.container.context.ContainerIdentifierService
import domino.DominoActivator
import java.util.UUID
import org.slf4j.LoggerFactory
import org.osgi.framework.ServiceRegistration
import scala.util.Try
import java.util.Properties
import scala.collection.JavaConverters._

class ContainerContextActivator extends DominoActivator {

  whenBundleActive {
    val log = LoggerFactory.getLogger(classOf[ContainerContextActivator])

    val containerContext = new ContainerContextImpl()

    val pid = classOf[ContainerIdentifierService].getPackage().getName()

    val confProps = Try { containerContext.readConfig(pid) }.getOrElse(new Properties())

    val uuid = Option(confProps.getProperty(ContainerIdentifierServiceImpl.PROP_UUID)) match {
      case Some(x) => x.toString()
      case None =>
        // generate and persist
        val newUuid = UUID.randomUUID().toString()
        log.info("About to write newly generated UUID: {}", newUuid)
        confProps.setProperty(ContainerIdentifierServiceImpl.PROP_UUID, newUuid)
        containerContext.writeConfig(pid, confProps)
        newUuid
    }

    val props = confProps.asScala.toMap.collect {
      case (k: String, v: String) if k.startsWith(ContainerIdentifierServiceImpl.PROP_PROPERTY)
        && k.length > ContainerIdentifierServiceImpl.PROP_PROPERTY.length() =>
        val realKey = k.substring(ContainerIdentifierServiceImpl.PROP_PROPERTY.length())
        log.info("Set identifier property [{}] to [{}]", Array(realKey, v): _*)
        realKey -> v
    }

    log.info("Container identifier is [{}]", uuid)
    val idService = new ContainerIdentifierServiceImpl(containerContext, uuid, props)
    val serviceReg = idService.providesService[ContainerIdentifierService]
  }

}