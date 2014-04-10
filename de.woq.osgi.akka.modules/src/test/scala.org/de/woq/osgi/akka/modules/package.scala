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

import java.util.Dictionary
import org.osgi.framework.{ BundleContext, ServiceReference }
import scala.collection.Map
import scala.collection.immutable.{ Map => IMap }
import org.scalatest.mock.MockitoSugar
import org.scalatest.{Matchers, WordSpec}
import org.mockito.Mockito.{when, verify}

class modulesSpec
  extends WordSpec
  with Matchers
  with MockitoSugar {

  "Calling a toRichBundleContext" should {
    "throw an IllegalArgumentException given a null BundleContext" in {
      intercept[IllegalArgumentException] {
        toRichBundleContext(null)
      }
    }
  }

  "A BundleContext" should {
    "be converted to a RichBundleContext implicitly" in {
      val context = mock[BundleContext]
      val richBundleContext: RichBundleContext = context
      richBundleContext should not be null
    }
  }

  "Calling a toRichServiceReference" should {
    "throw an IllegalArgumentException given a null ServiceReference" in {
      intercept[IllegalArgumentException] {
        toRichServiceReference(null)
      }
    }
  }

  "A ServiceReference" should {
    "be converted to a RichServiceReference implicitly" in {
      val serviceReference = mock[ServiceReference]
      val richServiceReference: RichServiceReference = serviceReference
      richServiceReference should not be null
    }
  }

  "A Pair" should {
    "be converted to a null Map implicitly when null" in {
      val tuple2: (String, String) = null
      val map: Map[String, String] = tuple2
      map should be (null)
    }
    "be converted to Some implicitly when not-null" in {
      val tuple2 = "Scala" -> "Modules"
      val map: Map[String, String] = tuple2
      map should have size 1
      map should contain ("Scala" -> "Modules")
    }
  }

  "Calling a stringToSimpleOpBuilder" should {
    "throw an IllegalArgumentException given a null String" in {
      intercept[IllegalArgumentException] {
        toSimpleOpBuilder(null)
      }
    }
  }

  "A String" should {
    "be converted to a SimpleOpBuilder implicitly" in {
      val simpleOpBuilder: SimpleOpBuilder = ""
      simpleOpBuilder should not be null
    }
  }

  "Calling a stringToPresentBuilder" should {
    "throw an IllegalArgumentException given a null String" in {
      intercept[IllegalArgumentException] {
        toPresentBuilder(null)
      }
    }
  }

  "A String" should {
    "be converted to a SimpleOpBuilder implicitly" in {
      val presentBuilder: PresentBuilder = ""
      presentBuilder should not be null
    }
  }

  "Calling interface with a given type" should {
    "return Some containing a class of that type" in {
      interface[String] should be (Some(classOf[String]))
    }
  }

  "Calling withInterface with a given type" should {
    "return a class of that type" in {
      withInterface[String] should be (classOf[String])
    }
  }

  "Calling scalaMapToJavaDictionary" should {
    val emptyScalaMap = IMap[Any, Any]()
    "return null given null" in {
      val javaDictionary: Dictionary[Any, Any] = scalaMapToJavaDictionary(null)
      javaDictionary should be null
    }
    "return an empty and immutable Java Dictionary given an empty Scala Map" in {
      val javaDictionary: Dictionary[Any, Any] = scalaMapToJavaDictionary(emptyScalaMap)
      javaDictionary should not be null
      javaDictionary.size should be 0
      javaDictionary.isEmpty should be true
      javaDictionary.keys.hasMoreElements should be false
      javaDictionary.elements.hasMoreElements should be false
      javaDictionary get "" should be null

      intercept[UnsupportedOperationException] {
        javaDictionary.put("", "")
      }

      intercept[UnsupportedOperationException] {
        javaDictionary remove ""
      }
    }
    "return an appropriate and immutable Java Dictionary given a non-empty Scala Map" in {
      val notEmptyScalaMap = IMap("a" -> "1")
      val javaDictionary = scalaMapToJavaDictionary(notEmptyScalaMap)
      javaDictionary should not be null
      javaDictionary.size should be 1
      javaDictionary.isEmpty should be false
      javaDictionary.keys.hasMoreElements should be true
      javaDictionary.elements.hasMoreElements should be true
      javaDictionary get "a" should be "1"

      intercept[UnsupportedOperationException] {
        javaDictionary.put("", "")
      }

      intercept[UnsupportedOperationException] {
        javaDictionary remove ""
      }
    }
  }

  "Calling invokeService" should {
    val context = mock[BundleContext]
    val serviceReference = mock[ServiceReference[String]]

    "result in appropriate calls to BundleContext and return None" in {
      when(context.getService(serviceReference)) thenReturn (null)
      context.getServiceReference("foo") should be (None)
      verify(context.ungetService(serviceReference)) should be 1
    }

//    "result in appropriate calls to BundleContext and return Some" in {
//      when(context.getService(serviceReference)) thenReturn ("Scala")
//      (invokeService(serviceReference, { s: String => s + "Modules" }, context) should be (Some("ScalaModules"))
//      //there was one(context).ungetService(serviceReference)
//    }
  }
}

class TestClass1
class TestClass2 extends TestInterface2
class TestClass3 extends TestInterface2 with TestInterface3
class TestClass4 extends TestInterface4

trait TestInterface1 {
  def name = getClass.getName
}
trait TestInterface2
trait TestInterface3
trait TestInterface4 extends TestInterface4a
trait TestInterface4a extends TestInterface4b
trait TestInterface4b extends TestInterface4c
trait TestInterface4c