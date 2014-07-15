package de.woq.blended.akka.itest

import org.slf4j.LoggerFactory

import scala.concurrent.duration._
import akka.actor.Props
import de.woq.blended.itestsupport.docker.{DockerClientFactory, DockerClientProvider, ContainerManager}
import de.woq.blended.testsupport.TestActorSys
import org.scalatest.junit.AssertionsForJUnit
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}

import de.woq.blended.itestsupport.docker.protocol._

class TestContainerManager extends ContainerManager with DockerClientProvider {
  override def getClient = {
    implicit val logger = context.system.log
    DockerClientFactory(context.system.settings.config)
  }
}

class BlendedDemoSpec extends TestActorSys
  with WordSpecLike
  with Matchers
  with AssertionsForJUnit
  with BeforeAndAfterAll {

  val logger = LoggerFactory.getLogger(classOf[BlendedDemoSpec].getName)

  "The demo container" should {

    "do something" in {
      System.setProperty("docker.io.version", "1.12")
      val mgr = system.actorOf(Props[TestContainerManager], "ContainerManager")
      mgr ! StartContainerManager
      expectMsg(ContainerManagerStarted)

      mgr ! GetContainerPorts("blended_demo_0")
      fishForMessage(10.seconds) {
        case ContainerPorts(ports) => {
          logger info ports.toString
          true
        }
        case _ => false
      }
    }
  }
}
