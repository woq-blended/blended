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

package blended.itestsupport.docker

import akka.event.LoggingAdapter
import blended.itestsupport.ContainerUnderTest
import blended.testsupport.TestActorSys
import com.typesafe.config.Config
import org.scalatest.mock.MockitoSugar
import org.scalatest.{Matchers, WordSpec}

class ContainerUnderTestSpec extends WordSpec
  with Matchers
  with DockerTestSetup
  with MockitoSugar {


  "The Container Under Test" should {

    "be configurable from the configuration" in TestActorSys { testkit =>
      implicit val system = testkit.system

      def docker = {
        System.setProperty("docker.io.version", "1.17")
        new Docker with VolumeBaseDir {
          override implicit val logger: LoggingAdapter = system.log
          override implicit val config: Config = system.settings.config
          override implicit val client = mockClient
        }
      }

      val cuts = ContainerUnderTest.containerMap(config)
      
      system.log.info(s"$cuts")
      
      cuts should have size ctNames.size
      
      cuts.get("jms_demo") should not be None
      cuts.get("blended_demo") should not be None
      
      cuts("jms_demo").url("http") should be("tcp://127.0.0.1:8181")
      cuts("jms_demo").url("http", "192.168.59.103") should be("tcp://192.168.59.103:8181")
      cuts("jms_demo").url("http", "192.168.59.103", "http") should be ("http://192.168.59.103:8181")
      
    }
  }
}
