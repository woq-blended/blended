package de.woq.blended.itestsupport.docker

import akka.event.LoggingAdapter
import com.typesafe.config.Config
import de.woq.blended.testsupport.TestActorSys
import org.scalatest.mock.MockitoSugar
import org.scalatest.{Matchers, WordSpecLike}

class DockerSpec extends TestActorSys
  with WordSpecLike
  with Matchers
  with DockerTestSetup
  with MockitoSugar {

  private def docker = {
    System.setProperty("docker.io.version", "1.12")
    new Docker {
      override implicit val logger: LoggingAdapter = system.log
      override implicit val config: Config = system.settings.config
      override implicit val client = mockClient
    }
  }

  "The Docker trait" should {

    "Read initialize from the configuration" in {
      docker.configuredContainers should have size(ctNames.size)

      ctNames.foreach { name =>
        val container = docker.configuredContainers.get(name).get
        container.containerName should be (name)
      }
    }
  }
}
