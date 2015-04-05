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

package de.wayofquality.blended.akka

import akka.actor.Props
import akka.pattern.{ask, pipe}
import akka.testkit.TestActorRef
import akka.util.Timeout
import com.typesafe.config.Config
import de.wayofquality.blended.akka.internal.OSGIFacade
import de.wayofquality.blended.akka.protocol.{BundleActorState, InitializeBundle, ServiceResult}
import de.wayofquality.blended.testsupport.TestActorSys
import org.osgi.framework.BundleContext
import org.scalatest.junit.AssertionsForJUnit
import org.scalatest.mock.MockitoSugar
import org.scalatest.{Matchers, WordSpec}

import scala.concurrent.Await
import scala.concurrent.duration._

object OSGIActorDummy {
  def apply()(implicit bundleContext: BundleContext) = new OSGIActorDummy() with InitializingActor[BundleActorState] with MemoryStash
}

class OSGIActorDummy extends InitializingActor[BundleActorState] with BundleName { this: MemoryStash =>

  override def bundleSymbolicName = "foo"


  override def createState(cfg: Config, bundleContext: BundleContext): BundleActorState = 
    BundleActorState(cfg, bundleContext)

  override def becomeWorking(state: BundleActorState): Unit = {
    unstash()
    super.becomeWorking(state)
  }

  def working(state: BundleActorState) : Receive = {
    case "invoke" => {
      invokeService[TestInterface1, String](classOf[TestInterface1]) { svc => svc.name } pipeTo sender
    }
  }

  override def receive : Receive = initializing orElse stashing
}

class OSGIActorSpec extends WordSpec
  with Matchers
  with AssertionsForJUnit {

  "An OSGIActor" should {

    implicit val timeout = Timeout(1.second)

    "allow to invoke a service" in new TestActorSys with TestSetup with MockitoSugar {
      val facade = system.actorOf(Props(OSGIFacade()), BlendedAkkaConstants.osgiFacadePath)

      val probe = TestActorRef(Props(OSGIActorDummy()), "testActor")

      probe ! InitializeBundle(osgiContext)

      Await.result(probe ?  "invoke", 3.seconds) match {
        case ServiceResult(Some(s)) => s should be("Andreas")
      }
    }
  }

}
