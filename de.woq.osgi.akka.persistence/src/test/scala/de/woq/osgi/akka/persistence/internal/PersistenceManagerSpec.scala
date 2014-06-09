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

import org.scalatest.{BeforeAndAfterAll, WordSpecLike, Matchers, WordSpec}
import com.typesafe.config.ConfigFactory
import java.io.File
import de.woq.osgi.java.testsupport.TestActorSys
import akka.event.Logging.Info
import de.woq.osgi.akka.persistence.internal.person.Person
import de.woq.osgi.akka.system.protocol.InitializeBundle
import akka.actor.{ActorRef, Props, PoisonPill}
import org.scalatest.mock.MockitoSugar
import de.woq.osgi.akka.system.internal.OSGIFacade
import de.woq.osgi.akka.system.WOQAkkaConstants
import scala.concurrent.duration._
import de.woq.osgi.akka.persistence.protocol.{ObjectStored, StoreObject}

class PersistenceManagerSpec
  extends TestActorSys
  with WordSpecLike
  with Matchers
  with PersistenceBundleName
  with TestSetup
  with MockitoSugar
  with BeforeAndAfterAll {

  var facade : ActorRef = _
  var pMgr : ActorRef = _

  override protected def beforeAll() {
    facade = system.actorOf(Props(OSGIFacade()), WOQAkkaConstants.osgiFacadePath)
    pMgr = system.actorOf(Props(PersistenceManager(new Neo4jBackend())), bundleSymbolicName)

    pMgr ! InitializeBundle(osgiContext)
  }

  override protected def afterAll() {
    pMgr ! PoisonPill
  }

  "The PersistenceManager" should {

    "Initialize correctly" in {

      system.eventStream.subscribe(self,classOf[Info])

      fishForMessage() {
        case Info(_, _, m : String) => m.startsWith("Initializing embedded Neo4j with path")
        case _ => false
      }
    }

    "Store a data object correctly" in {
      val info = new Person(firstName = "Andreas", name = "Gies")
      pMgr ! StoreObject(info)

      fishForMessage() {
        case ObjectStored(info) => true
        case _ => false
      }
    }
  }

}
