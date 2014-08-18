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

package de.woq.blended.samples.spray.helloworld.internal

import org.scalatest.{Matchers, WordSpec}
import spray.testkit.ScalatestRouteTest
import org.slf4j.LoggerFactory

class HelloRouteSpec extends WordSpec with Matchers with ScalatestRouteTest with HelloService {

  val log = LoggerFactory.getLogger(classOf[HelloRouteSpec])

  def actorRefFactory = system

  "The hello service" should {

    "return a hello message for a GET request to /hello" in {
      Get("/hello") ~> helloRoute ~> check {
        responseAs[String] should include("within OSGi")
      }
    }

    "leave GET to other paths unhandled" in {
      Get("/foo") ~> helloRoute ~> check {
        handled should be(false)
      }
    }
  }

}
