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

package de.woq.blended.itestsupport.docker

import akka.actor.Props
import akka.testkit.TestActorRef
import de.woq.blended.itestsupport.{PortRange, PortScanner}
import de.woq.blended.testsupport.TestActorSys
import org.scalatest.{Matchers, WordSpecLike}

import de.woq.blended.itestsupport.protocol._

class PortScannerSpec extends TestActorSys
  with WordSpecLike
  with Matchers {

  "The port scanner" should {

    val defaultRange = new PortRange {}
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

    "Return the next available port" in {
      val scanner = TestActorRef(Props(PortScanner(portCheck = checker)))
      scanner ! GetPort
      expectMsg(FreePort(defaultRange.fromPort))
      scanner ! GetPort
      expectMsg(FreePort(defaultRange.fromPort + 1))
    }
  }
}
