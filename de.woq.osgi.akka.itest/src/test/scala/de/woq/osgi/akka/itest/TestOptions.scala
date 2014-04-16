/*
 * Copyright 2013, WoQ - Way of Quality UG(mbH)
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

import org.ops4j.pax.exam.karaf.options.KarafDistributionOption._
import org.ops4j.pax.exam.CoreOptions._
import org.ops4j.pax.exam.{Option => PaxOption}
import org.ops4j.pax.exam.options.{MavenUrlReference, DefaultCompositeOption}
import java.io.File
import org.ops4j.pax.exam.karaf.options.LogLevelOption.LogLevel

trait ITestConfig {

  def featureUrl : MavenUrlReference
  def containerUrl : MavenUrlReference
  def featureUnderTest : String
}

class TestOptions { this : ITestConfig =>

  val scalaVersion = System.getProperty("scala.version")
  val karafVersion = System.getProperty("karaf.version")

  def woqAkkaFeatures : MavenUrlReference = featureUrl

  def karafOptions(useDeployFolder : Boolean = false) : PaxOption = new DefaultCompositeOption(
    karafDistributionConfiguration.frameworkUrl(containerUrl)
    .karafVersion(karafVersion).name("Apache Karaf").useDeployFolder(useDeployFolder).unpackDirectory(new File("target/paxexam/karaf")),
    features(woqAkkaFeatures, featureUnderTest),
    keepRuntimeFolder(),
    logLevel(LogLevel.INFO),
    editConfigurationFilePut("etc/config.properties", "karaf.framework", "equinox")
  )

  def testBundles(): PaxOption = {
    new DefaultCompositeOption(
      mavenBundle("com.typesafe.akka", "akka-testkit_%s".format(scalaVersion)).versionAsInProject,
      mavenBundle("org.scalatest", "scalatest_%s".format(scalaVersion)).versionAsInProject,
      junitBundles
    )
  }

  def karafOptionsWithTestBundles(useDeployFolder: Boolean = false): PaxOption = {
    new DefaultCompositeOption(
      karafOptions(useDeployFolder),
      testBundles()
    )
  }

}
