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

package de.woq.osgi.akka.persistence.internal

import org.scalatest.{Matchers, WordSpec}

class PersistenceProtocolSpec extends WordSpec with Matchers {

  import de.woq.osgi.akka.persistence.protocol._

  "The Persistence protocol" should {

    "implicitly convert primitives to PersistenceProperties" in {

      val bool : PersistenceProperty = true
      bool should be (BooleanProperty(true))

      val b : PersistenceProperty = 'c'.toByte
      b should be (ByteProperty('c'.toByte))

      val short : PersistenceProperty = 100.toShort
      short should be (ShortProperty(100))

      val int : PersistenceProperty = 100
      int should be (IntProperty(100))

      val long : PersistenceProperty = 100l
      long should be (LongProperty(100l))

      val float : PersistenceProperty = 2.0f
      float should be (FloatProperty(2.0f))

      val double : PersistenceProperty = 2.0
      double should be (DoubleProperty(2.0))

      val char : PersistenceProperty = 'c'
      char should be (CharProperty('c'))

      val string : PersistenceProperty = "Andreas"
      string should be (StringProperty("Andreas"))

      val iList : PersistenceProperty = 1 :: 2 :: Nil
      iList should be (ListProperty(List(IntProperty(1), IntProperty(2))))

      val lList : PersistenceProperty = 1l :: 2l :: Nil
      lList should be (ListProperty(List(LongProperty(1), LongProperty(2))))
    }

  }
}
