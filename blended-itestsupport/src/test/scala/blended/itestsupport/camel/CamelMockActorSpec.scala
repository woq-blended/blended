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

package blended.itestsupport.camel

import akka.actor.Props
import akka.camel.{CamelExtension, Oneway, Producer}
import akka.testkit.{TestActorRef, TestProbe}
import akka.util.Timeout
import blended.itestsupport.camel.MockAssertions._
import blended.itestsupport.camel.protocol._
import blended.itestsupport.docker.DockerTestSetup
import blended.testsupport.TestActorSys
import org.scalatest.mock.MockitoSugar
import org.scalatest.{Matchers, WordSpecLike}

import scala.concurrent.duration._

class CamelMockActorSpec extends TestActorSys
  with WordSpecLike
  with Matchers
  with DockerTestSetup
  with MockitoSugar {

  implicit val timeout = Timeout(3.seconds)
  
  val camel = CamelExtension(system)
  private[this] val log = system.log
  
  def mockActor(uri: String) = TestActorRef(Props(new CamelMockActor(s"direct-vm:$uri"))) 
  
  def producer(uri: String) = TestActorRef(Props(new Producer with Oneway {
    def endpointUri = s"direct-vm:$uri?block=true"    
  })) 
  
  def createProbe = {
    val probe = new TestProbe(system)
    system.eventStream.subscribe(probe.ref, classOf[MockMessageReceived])
    probe
  } 

  "The Camel Mock Actor" should {
    
    "Initialze with an empty set of received messages" in {
      val mock = mockActor("a")
      
      mock ! GetReceivedMessages 
      expectMsg(ReceivedMessages(List.empty))
    }
    
    "Should notify upon reception of a message on the event bus" in {
      val mock = mockActor("b") 
      val p = producer("b")
      
      val probe = createProbe

      p ! "Hello Andreas"
      probe.expectMsg(MockMessageReceived("direct-vm:b"))
    }
  
    "Track the received messages" in {
      
      val mock = mockActor("c") 
      val p = producer("c") 
      val probe = createProbe

      mock ! GetReceivedMessages 
      expectMsg(ReceivedMessages(List.empty))
      
      p ! "Hello Andreas"
      probe.expectMsg(MockMessageReceived("direct-vm:c"))

      mock ! GetReceivedMessages
      fishForMessage() {
        case list : ReceivedMessages => 
          list.messages.size == 1
        case _ => false
      }
    }

    "Allow execute a list of assertions and collect messages for failed assertions" in {
      val mock = mockActor("d") 
      val p = producer("d") 
      val probe = createProbe

      mock ! GetReceivedMessages 
      expectMsg(ReceivedMessages(List.empty))
      
      p ! "Hello Andreas"
      probe.expectMsg(MockMessageReceived("direct-vm:d"))

      checkAssertions(mock, expectedMessageCount(2)) should have size 1
    }  

    "Allow execute a list of assertions" in {
      val mock = mockActor("e") 
      val p = producer("e") 
      val probe = createProbe

      mock ! GetReceivedMessages 
      expectMsg(ReceivedMessages(List.empty))
      
      p ! "Hello Andreas"
      probe.expectMsg(MockMessageReceived("direct-vm:e"))

      checkAssertions(mock, expectedMessageCount(1)) should have size 0
    }  
  }
}