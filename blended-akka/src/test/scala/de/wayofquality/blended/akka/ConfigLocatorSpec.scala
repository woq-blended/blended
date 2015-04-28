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

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestActorRef, TestKit}
import com.typesafe.config.ConfigFactory
import de.wayofquality.blended.akka.internal.{ConfigDirectoryProvider, ConfigLocator}
import org.scalatest.junit.AssertionsForJUnit
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}


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

  class TestConfigLocator extends ConfigLocator with ConfigDirectoryProvider {
  
    override def fallback = Some(system.settings.config)
    override def configDirectory: String = "./target/test-classes" 
  }

  "ConfigLocator" should {

    "retreive a config object from an existing file" in {
      val locator = new TestConfigLocator
      val cfg = locator.getConfig("foo")
      cfg.getString("bar") should be ("YES")
    }

    "fall back to the actor system config if no file is found" in {
      val locator = new TestConfigLocator
      val cfg = locator.getConfig("bar")
      cfg.getString("bar") should be ("NO")
    }

    "respond with an empty config object if no file is found and the actor sys doesn't contain the config" in {
      val locator = new TestConfigLocator
      val cfg = locator.getConfig("nonsense")
      cfg.entrySet() should have size(0)
    }
  }

  override protected def afterAll() : Unit = {
    system.shutdown()
  }
}
