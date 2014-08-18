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

package de.woq.blended.persistence.internal

import de.woq.blended.container.context.ContainerContext
import org.osgi.framework.{Bundle, ServiceReference, BundleContext}
import org.mockito.Mockito._
import org.scalatest.mock.MockitoSugar

trait TestSetup { this : MockitoSugar =>

  implicit val osgiContext = mock[BundleContext]
  val ctContext = mock[ContainerContext]
  val ctContextRef = mock[ServiceReference[ContainerContext]]
  val bundle = mock[Bundle]

  when(ctContextRef.getBundle) thenReturn (bundle)
  when(bundle.getBundleContext) thenReturn (osgiContext)
  when(osgiContext.getServiceReference(classOf[ContainerContext])) thenReturn (ctContextRef)
  when(osgiContext.getService(ctContextRef)) thenReturn (ctContext)
  when(ctContext.getContainerConfigDirectory) thenReturn (getClass.getResource("/").getPath)
  when(ctContext.getContainerDirectory) thenReturn (getClass.getResource("/").getPath)

}