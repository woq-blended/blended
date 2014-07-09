package de.woq.blended.akka.itest

import de.woq.blended.itestsupport.docker.Docker
import org.scalatest.junit.AssertionsForJUnit
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpec}

class BlendedDemoSpec
  extends WordSpec
  with Matchers
  with AssertionsForJUnit
  with BeforeAndAfterAll
  with Docker {

  override protected def beforeAll() {
    configuredContainers.values.foreach(_.startContainer.waitContainer)
  }

  override protected def afterAll() {
    configuredContainers.values.foreach(_.stopContainer)
  }

  "The demo container" should {

    "do something" in {
      1 should be(1)
    }
  }
}
