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

package de.woq.blended.akka

import akka.actor.{Props, Actor, ActorSystem}
import akka.testkit.{TestActorRef, ImplicitSender, TestKit}
import com.typesafe.config.Config
import de.woq.blended.akka.internal.OSGIFacade
import de.woq.blended.akka.protocol.{ServiceResult, InitializeBundle}
import de.woq.blended.testsupport.TestActorSys
import org.osgi.framework.BundleContext
import org.scalatest.mock.MockitoSugar
import org.scalatest._
import scala.concurrent.duration._
import de.woq.blended.modules._

case object Available
case object Unavailable

object DummyWithDependencies {
  def apply()(implicit bundleContext: BundleContext) = new DummyWithDependencies()
}

class DummyWithDependencies extends ActorWithDependencies with BundleName {

  override def bundleSymbolicName = "foo"

  @ServiceDependency
  var testRef : Option[TestInterface2] = None

  def receive = initializing
}

@DoNotDiscover
class ActorWithDependenciesSpec extends WordSpec
  with Matchers {
  "An Actor with Dependencies" should {

    "start with all dependencies pending" in new TestActorSys with TestSetup with MockitoSugar {
      val facade = system.actorOf(Props(OSGIFacade()), BlendedAkkaConstants.osgiFacadePath)

      val probe = TestActorRef(Props(DummyWithDependencies()), "testActor")
      probe ! InitializeBundle(osgiContext)

      expectMsg(10.seconds, Available)
    }
  }
}
