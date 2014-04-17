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
package de.woq.osgi.akka.modules

import org.osgi.framework.{ServiceReference, BundleContext}
import org.scalatest.{WordSpec, Matchers}
import org.scalatest.junit.AssertionsForJUnit
import org.scalatest.mock.MockitoSugar
import java.util.concurrent.Executors
import scala.concurrent.{Await, Future, ExecutionContext}
import org.mockito.Mockito.{when, verify}
import scala.collection.JavaConversions._
import scala.util.{Success, Failure}

class ServicesFinderSpec extends WordSpec with Matchers with MockitoSugar with AssertionsForJUnit {

//  val es = Executors.newFixedThreadPool(2)
//  implicit val ec = ExecutionContext.fromExecutorService(es)
//
//  "Calling ServicesFinder.withFilter" should {
//
//    "throw an IllegalArgumentException given a null Filter" in {
//      val interface = classOf[TestInterface1]
//      val context = mock[BundleContext]
//
//      intercept[IllegalArgumentException] {
//        new ServicesFinder(interface, context) withFilter null
//      }
//    }
//  }
//
//  "Calling ServicesFinder.andApply" should {
//    val serviceReference = mock[ServiceReference[TestInterface1]]
//    val serviceReference2 = mock[ServiceReference[TestInterface1]]
//    val interface = classOf[TestInterface1]
//    val service = mock[TestInterface1]
//    val service2 = mock[TestInterface1]
//
//    "throw an IllegalArgumentException given a null function go be applied to the service" in {
//      val context = mock[BundleContext]
//
//      intercept[IllegalArgumentException] {
//        new ServicesFinder(interface, context) andApply (null: (TestInterface1 => Any))
//      }
//    }
//
//    "return Nil when there are no requested service references available" in {
//      val context = mock[BundleContext]
//
//      when(context.getServiceReferences(interface.getName, null)) thenReturn (null)
//      val servicesFinder = new ServicesFinder(interface, context)
//      servicesFinder andApply { _.name } should be (Nil)
//    }
//
//    "return None when there is a requested service references available but no service" in {
//      val context = mock[BundleContext]
//
//      when(context.getServiceReferences(interface, null)) thenReturn Array(serviceReference).toList
//      when(context.getService(serviceReference)) thenReturn (null)
//      val servicesFinder = new ServicesFinder(interface, context)
//
//      (Future.sequence (servicesFinder andApply { _.name }).mapTo[Seq[Option[String]]]).andThen {
//        case Success(results) => {
//          results should have size (1)
//          results should contain (None)
//          verify(context).ungetService(serviceReference)
//        }
//        case Failure(e) => {
//          fail("Error executing service invocations.")
//        }
//      }
//    }
//
//    "return a List with one element when there is one requested service reference with service available" in {
//      val context = mock[BundleContext]
//
//      when(context.getServiceReferences(interface, "(&(a=1)(b=*))")) thenReturn Array(serviceReference).toList
//      when(context.getService(serviceReference)) thenReturn (service)
//      when(service.name) thenReturn ("YES")
//
//      val servicesFinder = new ServicesFinder(interface, context, Some(("a" === "1") && "b".present))
//
//      (Future.sequence (servicesFinder andApply { _.name }).mapTo[Seq[Option[String]]]).andThen {
//        case Success(results) => {
//          results should have size (1)
//          results should contain(Some("YES"))
//          verify(context).ungetService(serviceReference)
//        }
//        case Failure(e) => {
//          fail("Error executing service invocations.")
//        }
//      }
//    }
//
//
//    "return a List with two elements when there are two requested service references with services available" in {
//      val context = mock[BundleContext]
//
//      when(context.getServiceReferences(interface, null)) thenReturn Array(serviceReference, serviceReference2).toList
//      when(context.getService(serviceReference)) thenReturn (service)
//      when(context.getService(serviceReference2)) thenReturn (service2)
//
//      when(service.name) thenReturn("YES")
//      when(service2.name) thenReturn ("NO")
//
//      val servicesFinder = new ServicesFinder(interface, context)
//
//      (Future.sequence (servicesFinder andApply { _.name }).mapTo[Seq[Option[String]]]).andThen {
//        case Success(results) => {
//          results should have size (2)
//          results should contain(Some("YES"))
//          results should contain(Some("NO"))
//          verify(context).ungetService(serviceReference)
//          verify(context).ungetService(serviceReference2)
//        }
//        case Failure(e) => {
//          fail("Error executing service invocations.")
//        }
//      }
//    }
//  }
}
