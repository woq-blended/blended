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

package de.wayofquality.blended.akka.itest

import org.scalatest.SpecLike
import de.wayofquality.blended.itestsupport.BlendedIntegrationTestSupport
import de.wayofquality.blended.testsupport.TestActorSys
import scala.collection.immutable.IndexedSeq
import org.scalatest.BeforeAndAfterAll
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.duration._
import akka.actor.Props
import de.wayofquality.blended.itestsupport.docker.protocol._

class BlendedDemoIntegrationSpec extends TestActorSys
  with SpecLike
  with BeforeAndAfterAll
  with BlendedIntegrationTestSupport {
  
  override def nestedSuites = IndexedSeq(new BlendedDemoSpec()(this))
  
  override def beforeAll() {
    system.actorOf(Props(new TestContainerProxy()), ctProxyName)
  }
  
  override def afterAll() {
    stopContainers(1200.seconds, this)
  }
}
