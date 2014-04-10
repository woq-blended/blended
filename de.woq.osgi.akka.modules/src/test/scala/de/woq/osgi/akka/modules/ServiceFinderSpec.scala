/*
 * Copyright 2009-2011 Weigle Wilczek GmbH
 * Modifications 2014- Way of Quality UG
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.woq.osgi.akka.modules

import org.osgi.framework.{ServiceReference, BundleContext}
import org.scalatest.{Matchers, WordSpec}
import org.scalatest.mock.MockitoSugar
import org.scalatest.junit.AssertionsForJUnit
import java.util.concurrent._
import scala.concurrent.{Await, ExecutionContext}
import org.mockito.Mockito.{when, verify}
import scala.concurrent.duration._

class ServiceFinderSpec extends WordSpec with Matchers with MockitoSugar with AssertionsForJUnit {

  "Calling ServiceFinder.andApply" should {

    val es = Executors.newFixedThreadPool(2)
    implicit val ec = ExecutionContext.fromExecutorService(es)

    "throw an IllegalArgumentException given a null function go be applied to the service" in {
      val context = mock[BundleContext]
      val interface = classOf[TestInterface1]

      intercept[IllegalArgumentException] {
        new ServiceFinder(interface, context) andApply (null: (TestInterface1 => Any))
      }
    }

    "return None when there is no requested service reference available" in {
      val context = mock[BundleContext]
      val interface = classOf[TestInterface1]

      when(context.getServiceReference(interface.getName)) thenReturn (null)

      // This creates a future executed in it's own thread
      val serviceFinder = new ServiceFinder(interface, context) andApply(_.name)

      Await.result(serviceFinder, 1.seconds) should be (None)
    }

    "return None when there is a requested service reference available but no service" in {
      val context = mock[BundleContext]
      val interface = classOf[TestInterface1]
      val serviceReference : ServiceReference[TestInterface1] = mock[ServiceReference[TestInterface1]]

      when((context.getServiceReference(classOf[TestInterface1]))) thenReturn (serviceReference, Nil: _*)
      when(context.getService(serviceReference)) thenReturn (null)

      // This creates a future executed in it's own thread
      val serviceFinder = new ServiceFinder(interface, context) andApply(_.name)

      // YES, await is bad, but we are in test world here
      Await.result(serviceFinder, 1.seconds) should be (None)

      // Make sure that we have released the sevice reference after the invocation
      verify(context).ungetService(serviceReference)
    }

    "return Some when there is a requested service reference with service available" in {
      val context = mock[BundleContext]
      val interface = classOf[TestInterface1]
      val serviceReference : ServiceReference[TestInterface1] = mock[ServiceReference[TestInterface1]]
      val service = mock[TestInterface1]

      when((context.getServiceReference(classOf[TestInterface1]))) thenReturn (serviceReference, Nil: _*)
      when(context.getService(serviceReference)) thenReturn (service)
      when(service.name) thenReturn ("YES")

      // This creates a future executed in it's own thread
      val serviceFinder = new ServiceFinder(interface, context) andApply(_.name)

      Await.result(serviceFinder, 1.seconds) should be (Some("YES"))
      verify(context).ungetService(serviceReference)
    }
  }
}
