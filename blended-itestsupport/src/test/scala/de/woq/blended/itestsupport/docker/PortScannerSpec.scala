package de.woq.blended.itestsupport.docker

import akka.actor.Props
import akka.testkit.TestActorRef
import de.woq.blended.itestsupport.{PortRange, PortScanner}
import de.woq.blended.testsupport.TestActorSys
import org.scalatest.{Matchers, WordSpecLike}

import de.woq.blended.itestsupport.protocol._

class DefaultRange extends PortRange

class PortScannerSpec extends TestActorSys
  with WordSpecLike
  with Matchers {

  "The port scanner" should {

    val defaultRange = new DefaultRange
    val checker = { p: Int => true }

    "initialize itself with the default ports" in {
      val scanner = TestActorRef(Props(PortScanner(portCheck = checker)))
      val realActor = scanner.underlyingActor.asInstanceOf[PortScanner with PortRange]
      realActor.fromPort should be(defaultRange.fromPort)
      realActor.toPort should be(defaultRange.toPort)
      realActor.minPortNumber should be(defaultRange.fromPort)
    }

    "Return the first available port" in {
      val scanner = TestActorRef(Props(PortScanner(portCheck = checker)))
      scanner ! GetPort
      expectMsg(FreePort(defaultRange.fromPort))
    }
  }
}
