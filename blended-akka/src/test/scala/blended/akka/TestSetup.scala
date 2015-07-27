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

import akka.actor.ActorSystem
import blended.container.context.{ContainerIdentifierService, ContainerContext}
import com.typesafe.config.ConfigFactory
import org.mockito.Mockito._
import org.osgi.framework.{Bundle, BundleContext, ServiceReference}
import org.scalatest.mock.MockitoSugar

trait TestSetup { this : MockitoSugar =>

  val idSvc = mock[ContainerIdentifierService]
  val ctContext = mock[ContainerContext]

  when(idSvc.getContainerContext()) thenReturn(ctContext)
  when(ctContext.getContainerConfigDirectory) thenReturn ("./target/test-classes")

  def bundleContext(b: Bundle): BundleContext = {
    
    val osgiContext = mock[BundleContext]
    
    val ctContextRef = mock[ServiceReference[ContainerContext]]
    val svcBundle = mock[Bundle]
    val idSvcRef = mock[ServiceReference[ContainerIdentifierService]]

    val idName : String = classOf[ContainerIdentifierService].getName()
    val ccName : String = classOf[ContainerContext].getName()

    when[ServiceReference[_]](osgiContext.getServiceReference(ccName)) thenReturn (ctContextRef)
    when(osgiContext.getService(ctContextRef)) thenReturn (ctContext)

    when[ServiceReference[_]](osgiContext.getServiceReference(idName)) thenReturn(idSvcRef)
    when(osgiContext.getService(idSvcRef)) thenReturn(idSvc)

    when(svcBundle.getSymbolicName()) thenReturn("foo")
    when(svcBundle.getBundleContext) thenReturn (osgiContext)

    osgiContext
  }
  
  def testActorConfig(symbolicName : String, system: ActorSystem) : OSGIActorConfig = {
    val b = mock[Bundle]
    val bCtxt = bundleContext(b)
    
    when(bCtxt.getBundle()) thenReturn(b)
    when(b.getBundleContext()) thenReturn(bCtxt)
    when(b.getSymbolicName()) thenReturn(symbolicName)
    
    OSGIActorConfig(bCtxt, system, ConfigFactory.load("listener.conf"), idSvc)
  }
}