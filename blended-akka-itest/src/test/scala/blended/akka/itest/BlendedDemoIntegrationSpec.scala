/*
 * Copyright 2014ff,  https://github.com/woq-blended
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

package blended.akka.itest

import akka.actor.{ActorSystem, Props}
import akka.testkit.TestKit
import blended.itestsupport.BlendedIntegrationTestSupport
import org.scalatest.{BeforeAndAfterAll, Spec}

import scala.collection.immutable.IndexedSeq
import scala.concurrent.duration._

class BlendedDemoIntegrationSpec extends Spec
  with BeforeAndAfterAll
  with BlendedIntegrationTestSupport {

  implicit val testkit = new TestKit(ActorSystem("Blended"))
  
  private[this] val ctProxy = testkit.system.actorOf(Props(new TestContainerProxy()))
  private[this] val timeout = 1200.seconds
  
  override def nestedSuites = IndexedSeq(new BlendedDemoSpec())
  
  override def beforeAll() {
    testContext(ctProxy)(timeout, testkit)
    containerReady(ctProxy)(timeout, testkit)
  }
  
  override def afterAll() {
    stopContainers(ctProxy)(1200.seconds, testkit)
  }
}
