package de.woq.blended.docker.demo

import org.scalatest.{WordSpecLike, Matchers}

class DockerDemoSpec extends WordSpecLike with Matchers {

  "The demo container" should {

    "Start correctly" in {
      1 should be(1)
    }
  }
}
