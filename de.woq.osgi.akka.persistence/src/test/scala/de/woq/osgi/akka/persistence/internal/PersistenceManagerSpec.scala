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

import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpec}
import com.typesafe.config.ConfigFactory
import java.io.File
import de.woq.osgi.java.testsupport.TestActorSys
import akka.actor.ActorSystem
import akka.event.Logging.Info
import de.woq.osgi.java.container.registry.protocol.ContainerInfo

class PersistenceManagerSpec extends WordSpec with Matchers with PersistenceBundleName with BeforeAndAfterAll {

  "The PersistenceManager" should {

    val configPath = classOf[PersistenceManagerSpec].getResource("/").getPath

//    "Initialize correctly" in new TestActorSys {
//
//      implicit val logging = system.log
//      val backend = new Neo4jBackend()
//
//      system.eventStream.subscribe(self,classOf[Info])
//
//      backend.initBackend(configPath, ConfigFactory.parseFile(new File(configPath, s"$bundleSymbolicName.conf") ))
//      backend.shutdownBackend()
//
//      fishForMessage() {
//        case Info(_, _, m : String) => m.startsWith("Initializing embedded Neo4j with path")
//      }
//
//      fishForMessage() {
//        case Info(_, _, m : String) => m.startsWith("Shutting down embedded Neo4j for path")
//      }
//    }

    "Store a data object correctly" in new TestActorSys {

      implicit val logging = system.log
      val backend = new Neo4jBackend()

      system.eventStream.subscribe(self,classOf[Info])

      backend.initBackend(configPath, ConfigFactory.parseFile(new File(configPath, s"$bundleSymbolicName.conf") ))

      val info = new ContainerInfo("uuid", Map("name" -> "Andreas Gies", "city" -> "Cadiz"))
      backend.store(info)

      backend.shutdownBackend()

      fishForMessage() {
        case Info(_, _, m : String) => m.startsWith("Initializing embedded Neo4j with path")
      }

      fishForMessage() {
        case Info(_, _, m : String) => m.startsWith("Shutting down embedded Neo4j for path")
      }
    }
  }

}
