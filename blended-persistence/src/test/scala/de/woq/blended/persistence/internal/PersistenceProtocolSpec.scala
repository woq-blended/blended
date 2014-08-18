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

import org.scalatest.{Matchers, WordSpec}

class PersistenceProtocolSpec extends WordSpec with Matchers {

  import de.woq.blended.persistence.protocol._

  "The Persistence protocol" should {

    "implicitly convert primitives to PersistenceProperties" in {

      val bool : PersistenceProperty[Boolean] = true
      bool should be (PersistenceProperty[Boolean](true))

      val b : PersistenceProperty[Byte] = 'c'.toByte
      b should be (PersistenceProperty[Byte]('c'.toByte))

      val short : PersistenceProperty[Short] = 100.toShort
      short should be (PersistenceProperty[Short](100))

      val int : PersistenceProperty[Int] = 100
      int should be (PersistenceProperty[Int](100))

      val long : PersistenceProperty[Long] = 100l
      long should be (PersistenceProperty[Long](100l))

      val float : PersistenceProperty[Float] = 2.0f
      float should be (PersistenceProperty[Float](2.0f))

      val double : PersistenceProperty[Double] = 2.0
      double should be (PersistenceProperty[Double](2.0))

      val char : PersistenceProperty[Char] = 'c'
      char should be (PersistenceProperty[Char]('c'))

      val string : PersistenceProperty[String] = "Andreas"
      string should be (PersistenceProperty[String]("Andreas"))
    }

    "implicitly convert properties to primitives" in {
      val string : PersistenceProperty[String] = "Andreas"
      val string2 : String = string
      string2 should be ("Andreas")
    }

  }
}
