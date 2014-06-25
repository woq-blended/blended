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

import org.scalatest.junit.AssertionsForJUnit
import org.scalatest.mock.MockitoSugar
import org.scalatest.{Matchers, WordSpec}

class FilterSpec extends WordSpec with Matchers with AssertionsForJUnit {

  """"x" === 1 (equal)""" should {
    """be converted into the filter string "(x=1)" """ in {
      val filter: Filter = "x" === 1
      filter.toString should be ("(x=1)")
    }
  }

  """~"x" (present)""" should {
    """be converted into the filter string "(x=*)" """ in {
      val filter: Filter = ~"x"
      filter.toString should be ("(x=*)")
    }
  }

  """!(("x" === 1) && ("y" <== 2) || (("z" >== "3") and ("a" ~== 9)) or "z".present)""" should {
    """be converted into the filter string "(!(|(&(x=1)(y<=2))(&(z>=3)(a~=9))(z=*)))" """ in {
      val filter: Filter =
        !(("x" === 1) && ("y" <== 2) || (("z" >== "3") and ("a" ~== 9)) or "z".present)
      filter.toString should be ("(!(|(&(x=1)(y<=2))(&(z>=3)(a~=9))(z=*)))")
    }
  }
}

class AndBuilderSpec extends WordSpec with MockitoSugar with Matchers with AssertionsForJUnit {

  "Calling AndBuilder.&&" should {
    val component = mock[FilterComponent]
    "throw an IllegalArgumentException given null" in {
      intercept[IllegalArgumentException] {
        new AndBuilder(component) && null
      }
    }
  }

  "Calling AndBuilder.and" should {
    val component = mock[FilterComponent]
    "throw an IllegalArgumentException given null" in {
      intercept[IllegalArgumentException] {
        new AndBuilder(component) and null
      }
    }
  }

  "Calling OrBuilder.||" should {
    val component = mock[FilterComponent]
    "throw an IllegalArgumentException given null" in {
      intercept[IllegalArgumentException] {
        new OrBuilder(component) || null
      }
    }
  }

  "Calling OrBuilder.or" should {
    val component = mock[FilterComponent]
    "throw an IllegalArgumentException given null" in {
      intercept[IllegalArgumentException] {
        new OrBuilder(component) or null
      }
    }
  }

  "Calling SimpleOpBuilder.===" should {
    "throw an IllegalArgumentException given null" in {
      intercept[IllegalArgumentException] {
        new SimpleOpBuilder("") === null
      }
    }
  }

  "Calling SimpleOpBuilder.equal" should {
    "throw an IllegalArgumentException given null" in {
      intercept[IllegalArgumentException] {
        new SimpleOpBuilder("") equal null
      }
    }
  }

  "Calling SimpleOpBuilder.~==" should {
    "throw an IllegalArgumentException given null" in {
      intercept[IllegalArgumentException] {
        (new SimpleOpBuilder("") ~== null)
      }
    }
  }

  "Calling SimpleOpBuilder.approx" should {
    "throw an IllegalArgumentException given null" in {
      intercept[IllegalArgumentException] {
        new SimpleOpBuilder("") approx null
      }
    }
  }

  "Calling SimpleOpBuilder.>==" should {
    "throw an IllegalArgumentException given null" in {
      intercept[IllegalArgumentException] {
        (new SimpleOpBuilder("") >== null)
      }
    }
  }

  "Calling SimpleOpBuilder.ge" should {
    "throw an IllegalArgumentException given null" in {
      intercept[IllegalArgumentException] {
        new SimpleOpBuilder("") ge null
      }
    }
  }

  "Calling SimpleOpBuilder.greaterEqual" should {
    "throw an IllegalArgumentException given null" in {
      intercept[IllegalArgumentException] {
        new SimpleOpBuilder("") greaterEqual null
      }
    }
  }

  "Calling SimpleOpBuilder.<==" should {
    "throw an IllegalArgumentException given null" in {
      intercept[IllegalArgumentException] {
        (new SimpleOpBuilder("") <== null)
      }
    }
  }

  "Calling SimpleOpBuilder.le" should {
    "throw an IllegalArgumentException given null" in {
      intercept[IllegalArgumentException] {
        new SimpleOpBuilder("") le null
      }
    }
  }

  "Calling SimpleOpBuilder.lessEqual" should {
    "throw an IllegalArgumentException given null" in {
      intercept[IllegalArgumentException] {
        new SimpleOpBuilder("") lessEqual null
      }
    }
  }
}
