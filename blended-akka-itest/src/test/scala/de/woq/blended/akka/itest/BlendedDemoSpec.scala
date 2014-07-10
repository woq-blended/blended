package de.woq.blended.akka.itest

import org.slf4j.LoggerFactory

import scala.concurrent.duration._
import akka.actor.Props
import de.woq.blended.itestsupport.docker.ContainerManager
import de.woq.blended.testsupport.TestActorSys
import org.scalatest.junit.AssertionsForJUnit
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}

import de.woq.blended.itestsupport.docker.protocol._

class BlendedDemoSpec extends TestActorSys
  with WordSpecLike
  with Matchers
  with AssertionsForJUnit
  with BeforeAndAfterAll {

  val logger = LoggerFactory.getLogger(classOf[BlendedDemoSpec].getName)

  "The demo container" should {

    "do something" in {
      val mgr = system.actorOf(Props[ContainerManager], "ContainerManager")
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
