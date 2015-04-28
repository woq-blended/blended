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

package de.wayofquality.blended.persistence.neo4j.internal

import akka.actor.ActorSystem
import de.wayofquality.blended.container.context.ContainerContext
import de.wayofquality.blended.testsupport.TestActorSys
import org.mockito.Mockito._
import org.osgi.framework.{Bundle, BundleContext, ServiceReference}
import org.scalatest.mock.MockitoSugar

/**
 * Created by andreas on 28/04/15.
 */
trait TestSetup { this : TestActorSys with MockitoSugar =>

  implicit val osgiContext = mock[BundleContext]
  val ctContext = mock[ContainerContext]
  val ctContextRef = mock[ServiceReference[ContainerContext]]
  val bundle = mock[Bundle]
  val actorSystemRef = mock[ServiceReference[ActorSystem]]

  when(ctContextRef.getBundle) thenReturn (bundle)
  when(bundle.getBundleContext) thenReturn (osgiContext)
  when(osgiContext.getServiceReference(classOf[ContainerContext])) thenReturn (ctContextRef)
  when(osgiContext.getService(ctContextRef)) thenReturn (ctContext)
  when(osgiContext.getServiceReference(classOf[ActorSystem])) thenReturn (actorSystemRef)
  when(osgiContext.getService(actorSystemRef)) thenReturn system
  when(ctContext.getContainerConfigDirectory) thenReturn (getClass.getResource("/").getPath)
  when(ctContext.getContainerDirectory) thenReturn (getClass.getResource("/").getPath)

}
