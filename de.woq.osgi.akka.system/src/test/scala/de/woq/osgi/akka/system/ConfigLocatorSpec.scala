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

package de.woq.osgi.akka.system

import akka.testkit.{ImplicitSender, TestActorRef, TestKit}
import akka.actor.ActorSystem
import org.scalatest.{BeforeAndAfterAll, WordSpecLike, Matchers}
import org.scalatest.junit.AssertionsForJUnit
import de.woq.osgi.akka.system.internal.{ConfigDirectoryProvider, ConfigLocator}
import com.typesafe.config.ConfigFactory

import protocol._

class TestConfigLocator extends ConfigLocator with ConfigDirectoryProvider {
  override def configDirectory: String = getClass.getResource("/").getPath
}

class ConfigLocatorSpec extends TestKit(ActorSystem("ConfigLocator", ConfigFactory.parseString(
  """
    bar {
        bar = "NO"
    }
  """
)))
  with WordSpecLike
  with Matchers
  with AssertionsForJUnit
  with BeforeAndAfterAll
  with ImplicitSender {

  trait TestSetup {
    def locator = TestActorRef[TestConfigLocator]
  }

  "ConfigLocator" should {

    "respond with a config object read from a file" in new TestSetup {
      locator ! ConfigLocatorRequest("foo")
      expectMsgAllClassOf(classOf[ConfigLocatorResponse]) foreach { m =>
        m.config.getString("bar") should be ("YES")
      }
    }

    "fall back to the actor system config if no file is found" in new TestSetup {
      locator ! ConfigLocatorRequest("bar")
      expectMsgAllClassOf(classOf[ConfigLocatorResponse]) foreach { m =>
        m.config.getString("bar") should be ("NO")
      }
    }

    "respond with an empty config object if no file is found and the actor sys doesn't contain the config" in new TestSetup {
      locator ! ConfigLocatorRequest("nonsense")
      expectMsgAllClassOf(classOf[ConfigLocatorResponse]) foreach { m =>
        m.config.entrySet should have size(0)
      }
    }
  }

  override protected def afterAll() {
    system.shutdown()
  }
}
