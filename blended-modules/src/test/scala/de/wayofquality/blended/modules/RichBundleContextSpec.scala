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

package de.wayofquality.blended.modules

import org.mockito.ArgumentCaptor
import org.mockito.Mockito.verify
import org.osgi.framework.BundleContext
import org.scalatest.mock.MockitoSugar
import org.scalatest.{Matchers, WordSpecLike}
import de.wayofquality.blended.testsupport.TestActorSys

class RichBundleContextSpec extends TestActorSys() with WordSpecLike with MockitoSugar with Matchers {

  implicit val logger = system.log

  "Calling RichBundleContext.createService" should {

    "throw an IllegalArgumentException given a null service object" in {
      val context = mock[BundleContext]

      intercept[IllegalArgumentException] {
        new RichBundleContext(context).createService(null)
      }
    }

    "throw an IllegalArgumentException given null service properties" in {
      val context = mock[BundleContext]
      val service = new TestClass1

      intercept[IllegalArgumentException] {
        new RichBundleContext(context).createService(service, null)
      }
    }

    "call BundleContext.registerService with the class of the given service object itself when it does not implement a service interface" in {
      val context = mock[BundleContext]
      val service = new TestClass1

      new RichBundleContext(context).createService(service)
      verify(context).registerService(Array(classOf[TestClass1].getName), service, null)
    }

    "call BundleContext.registerService with the one interface implemented by the given service object" in {
      val context = mock[BundleContext]
      val service = new TestClass2
      new RichBundleContext(context).createService(service)

      verify(context).registerService(Array(classOf[TestInterface2].getName), service, null)
    }

    "call BundleContext.registerService with the interfaces implemented by the given service object" in {
      val context = mock[BundleContext]
      val service = new TestClass3
      new RichBundleContext(context).createService(service)
      verify(context).registerService(Array(classOf[TestInterface2].getName, classOf[TestInterface3].getName), service, null)
    }

    "call BundleContext.registerService with all inherited interfaces implemented by the given service object" in {
      val context = mock[BundleContext]
      val service = new TestClass4
      new RichBundleContext(context).createService(service)
      verify(context).registerService(Array(
        classOf[TestInterface4c].getName, classOf[TestInterface4b].getName,
        classOf[TestInterface4a].getName, classOf[TestInterface4].getName),
        service, null
      )
    }

    "call BundleContext.registerService with the given service properties" in {

      val context = mock[BundleContext]
      val service = new TestClass1

      val ifaceCaptor = ArgumentCaptor.forClass(classOf[Array[String]])
      val propertiesCaptor = ArgumentCaptor.forClass(classOf[java.util.Dictionary[String, Any]])
      val svcCaptor = ArgumentCaptor.forClass(classOf[AnyRef])
      new RichBundleContext(context).createService(service, Map("a" -> "b"))

      verify(context).registerService(
        ifaceCaptor.capture(),
        svcCaptor.capture(),
        propertiesCaptor.capture()
      )

      propertiesCaptor.getValue.size should be (1)
      propertiesCaptor.getValue get "a" should be ("b")
    }
  }

  "Calling RichBundleContext.findService" should {
    val context = mock[BundleContext]
    "throw an IllegalArgumentException given a null service interface" in {
      intercept[IllegalArgumentException] {
        new RichBundleContext(context).findService(null.asInstanceOf[Class[TestInterface1]])
      }
    }

    "return a not-null ServiceFinder with the correct interface" in {
      val serviceFinder = new RichBundleContext(mock[BundleContext]).findService(classOf[TestInterface1])
      serviceFinder should not be (null)
    }
  }

//  "Calling RichBundleContext.findServices" should {
//    val context = mock[BundleContext]
//    "throw an IllegalArgumentException given a null service interface" in {
//      intercept[IllegalArgumentException] {
//        new RichBundleContext(context).findServices(null.asInstanceOf[Class[TestInterface1]])
//      }
//    }
//
//    "return a not-null ServiceFinder with the correct interface" in {
//      val servicesFinder = new RichBundleContext(mock[BundleContext]).findServices(classOf[TestInterface1])
//      servicesFinder should not be null
//    }
//  }
//
//  "Calling RichBundleContext.watchServices" should {
//    val context = mock[BundleContext]
//    "throw an IllegalArgumentException given a null service interface" in {
//      intercept[IllegalArgumentException] {
//        new RichBundleContext(context).watchServices(null.asInstanceOf[Class[TestInterface1]])
//      }
//    }
//    "return a not-null ServiceWatcher with the correct interface" in {
//      val servicesWatcher = new RichBundleContext(mock[BundleContext]).watchServices(classOf[TestInterface1])
//      servicesWatcher should not be null
//    }
//  }
}
