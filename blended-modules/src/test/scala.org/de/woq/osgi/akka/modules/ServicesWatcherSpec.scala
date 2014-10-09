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

package de.woq.blended.akka.modules

import org.osgi.framework.BundleContext
import org.scalatest.{Matchers, WordSpec}
import org.scalatest.mock.MockitoSugar

class ServicesWatcherSpec extends WordSpec with MockitoSugar with Matchers {

  "Calling ServicesWatcher.withFilter" should {
    "throw an IllegalArgumentException given a null Filter" in {
      intercept[IllegalArgumentException] {
        new ServicesWatcher(classOf[String], mock[BundleContext]) withFilter null
      }
    }
  }

  "Calling ServicesWatcher.andHandle" should {
    "throw an IllegalArgumentException given a null partial function to handle ServiceEvents" in {
      intercept[IllegalArgumentException] {
        new ServicesWatcher(classOf[String], mock[BundleContext]).andHandle(null)
      }
    }
  }
}

class ServiceEventSpec extends WordSpec with MockitoSugar with Matchers {

  "Creating a ServiceEvent (subclass)" should {
    "throw an IllegalArgumentException given a null service" in {
      intercept[IllegalArgumentException] {
        new AddingService(null, Map[String, Any]())
      }
    }

    "throw an IllegalArgumentException given null service properties" in {
      intercept[IllegalArgumentException] {
        new AddingService("", null)
      }
    }
  }
}
