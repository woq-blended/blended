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
package de.woq.blended.modules

import java.util.concurrent._

import de.woq.blended.testsupport.TestActorSys
import org.mockito.Mockito.when
import org.osgi.framework.{BundleContext, ServiceReference}
import org.scalatest.junit.AssertionsForJUnit
import org.scalatest.mock.MockitoSugar
import org.scalatest.{Matchers, WordSpecLike}

import scala.concurrent.ExecutionContext

class ServiceFinderSpec extends TestActorSys with WordSpecLike with Matchers with MockitoSugar with AssertionsForJUnit {

  implicit val logger = system.log

  "Calling findService" should {

    val es = Executors.newFixedThreadPool(2)
    implicit val ec = ExecutionContext.fromExecutorService(es)

    "throw an IllegalArgumentException when called with a null interface" in {
      val context = mock[BundleContext]
      val richContext : RichBundleContext = context

      intercept[IllegalArgumentException] {
        richContext.findService(null)
      }
    }

    "return None when there is no requested service reference available" in {
      val context = mock[BundleContext]
      val interface = classOf[TestInterface1]
      val richContext : RichBundleContext = context

      when(context.getServiceReference(interface)) thenReturn (null)

      val svcRef = richContext.findService(interface)

      svcRef should be (None)
    }

    "return Some when we have a service reference exists" in {
      val context = mock[BundleContext]
      val interface = classOf[TestInterface1]
      val serviceReference : ServiceReference[TestInterface1] = mock[ServiceReference[TestInterface1]]
      val richContext : RichBundleContext = context

      when(context.getServiceReference(interface)) thenReturn (serviceReference)

      (richContext.findService(interface)) should be (Some(serviceReference))
    }
//
//    "return None when there is a requested service reference available but no service" in {
//      val context = mock[BundleContext]
//      val interface = classOf[TestInterface1]
//      val serviceReference : ServiceReference[TestInterface1] = mock[ServiceReference[TestInterface1]]
//
//      when((context.getServiceReference(classOf[TestInterface1]))) thenReturn (serviceReference, Nil: _*)
//      when(context.getService(serviceReference)) thenReturn (null)
//
//      // This creates a future executed in it's own thread
//      val serviceFinder = new ServiceFinder(interface, context) andApply(_.name)
//
//      // YES, await is bad, but we are in test world here
//      Await.result(serviceFinder, 1.seconds) should be (None)
//
//      // Make sure that we have released the sevice reference after the invocation
//      verify(context).ungetService(serviceReference)
//    }
//
//    "return Some when there is a requested service reference with service available" in {
//      val context = mock[BundleContext]
//      val interface = classOf[TestInterface1]
//      val serviceReference : ServiceReference[TestInterface1] = mock[ServiceReference[TestInterface1]]
//      val service = mock[TestInterface1]
//
//      when((context.getServiceReference(classOf[TestInterface1]))) thenReturn (serviceReference, Nil: _*)
//      when(context.getService(serviceReference)) thenReturn (service)
//      when(service.name) thenReturn ("YES")
//
//      // This creates a future executed in it's own thread
//      val serviceFinder = new ServiceFinder(interface, context) andApply(_.name)
//
//      Await.result(serviceFinder, 1.seconds) should be (Some("YES"))
//      verify(context).ungetService(serviceReference)
//    }
  }
}
