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

package de.wayofquality.blended.akka

import com.typesafe.config.Config
import de.wayofquality.blended.container.context.ContainerIdentifierService
import org.osgi.framework.BundleContext

case class OSGIActorConfig (
  bundleContext: BundleContext,
  idSvc: ContainerIdentifierService
) {

  private lazy val configLocator = new ConfigLocator(idSvc.getContainerContext().getContainerConfigDirectory())

  val config : Config =
    configLocator.getConfig(bundleContext.getBundle().getSymbolicName())
} 
