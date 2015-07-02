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

class ContainerContextActivator extends DominoActivator {

  whenBundleActive {
    val containerContext = new ContainerContextImpl()

    val pid = classOf[ContainerIdentifierService].getPackage().getName()

    whenConfigurationActive(pid) { conf =>

      val log = LoggerFactory.getLogger(classOf[ContainerContextActivator])

      val uuidOption = conf.get(ContainerIdentifierServiceImpl.PROP_UUID)

      val uuid = uuidOption match {
        case Some(x) => x.toString()
        case None =>
          // generate and persist
          val newUuid = UUID.randomUUID().toString()
          log.info("About to write newly generated UUID: {}", newUuid)
          val toStore = containerContext.readConfig(pid)
          toStore.setProperty(ContainerIdentifierServiceImpl.PROP_UUID, newUuid)
          containerContext.writeConfig(pid, toStore)
          newUuid
      }

      val props = conf.collect {
        case (k: String, v: String) if v.startsWith(ContainerIdentifierServiceImpl.PROP_PROPERTY)
          && v.length > ContainerIdentifierServiceImpl.PROP_PROPERTY.length() =>
          val realKey = k.substring(ContainerIdentifierServiceImpl.PROP_PROPERTY.length())
          log.info("Set identifier property [{}] to [{}]", Array(realKey, v): _*)
          realKey -> v
      }

      log.info("Container identifier is [{}]", uuid)

      val idService = new ContainerIdentifierServiceImpl(containerContext, uuid, props)
      idService.providesService[ContainerIdentifierService]

    }
  }

}