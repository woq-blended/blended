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

import org.osgi.framework.ServiceReference
import org.scalatest.{Matchers, WordSpec}
import org.scalatest.mock.MockitoSugar
import org.mockito.Mockito.when

class RichServiceReferenceSpec extends WordSpec with MockitoSugar with Matchers {

  "Calling RichServiceReference.properties" should {
    val serviceReference = mock[ServiceReference[TestClass1]]

    "return the empty Map given there are no service properties" in {
      when(serviceReference.getPropertyKeys) thenReturn (Array[String]())
      new RichServiceReference(serviceReference).properties should be (Map.empty)
    }

    "return a Map containing (a -> a) and (b -> b) given there are service properties a=1 and b=b" in {
      when(serviceReference.getPropertyKeys).thenReturn(Array("a", "b"))
      when(serviceReference.getProperty("a")).thenReturn("a", Nil: _*)
      when(serviceReference.getProperty("b")).thenReturn("b", Nil: _*)

      val svcRef = new RichServiceReference(serviceReference)

      svcRef.properties should have size 2
      svcRef.properties should contain ("a" -> "a")
      svcRef.properties should contain ("b" -> "b")
    }
  }
}
