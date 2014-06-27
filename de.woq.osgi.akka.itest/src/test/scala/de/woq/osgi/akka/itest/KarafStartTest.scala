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

package de.woq.osgi.akka.itest

import de.woq.blended.container.registry.protocol.ContainerInfo
import org.junit.runner.RunWith
import org.ops4j.pax.exam.junit.PaxExam
import org.scalatest.junit.{AssertionsForJUnit, JUnitSuite}
import org.scalatest.Matchers
import org.junit.{Before, Test}
import org.ops4j.pax.exam.{Option => PaxOption, Configuration}
import akka.actor.ActorSystem
import javax.inject.Inject
import org.ops4j.pax.exam.CoreOptions._
import akka.testkit.TestProbe
import akka.event.Logging.Info

@RunWith(classOf[PaxExam])
class KarafStartTest extends JUnitSuite with Matchers with AssertionsForJUnit {

  val testOptions = new TestOptions with ITestConfig {
    override def containerUrl = maven()
      .groupId("de.woq.blended")
      .artifactId("blended-karaf-central")
      .versionAsInProject()
      .`type`("tar.gz")
      .classifier("nojre")

    override def featureUrl = maven()
      .groupId("de.woq.blended")
      .artifactId("blended-karaf-features")
      .versionAsInProject()
      .`type`("xml")
      .classifier("features")

    override def featureUnderTest = "blended-akka"
  }

  @Inject
  var system : ActorSystem = _

  var testProbe : TestProbe = _

  @Before
  def setupTestkit() {
    testProbe = new TestProbe(system)
    system.eventStream.subscribe(testProbe.ref, classOf[Info])
  }

  @Configuration
  def config : Array[PaxOption] = Array(testOptions.karafOptionsWithTestBundles())

  @Test
  def karafStartTest() {
    import scala.concurrent.duration._

    system should not be (null)

    testProbe.fishForMessage(1.minute) {
      case Info(_, _, m) => m.toString.startsWith(classOf[ContainerInfo].getSimpleName)
    }
  }

}
