package de.woq.blended.itestsupport.camel

import akka.camel.Consumer
import akka.camel.CamelMessage
import akka.testkit.TestActors
import org.scalatest.Matchers
import akka.camel.CamelExtension
import akka.actor.ActorSystem
import de.woq.blended.testsupport.TestActorSys
import org.scalatest.WordSpecLike
import de.woq.blended.itestsupport.docker.DockerTestSetup
import org.scalatest.mock.MockitoSugar
import akka.testkit.TestActorRef
import akka.actor.Props
import de.woq.blended.itestsupport.camel.protocol._
import akka.camel.Producer
import akka.camel.Oneway
import akka.testkit.TestProbe
import de.woq.blended.itestsupport.camel.MockAssertions._

class CamelMockActorSpec extends TestActorSys
  with WordSpecLike
  with Matchers
  with DockerTestSetup
  with MockitoSugar {

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

      mock ! CheckAssertions(expectedMessageCount(2))

      val r = receiveN(1).toList.head.asInstanceOf[CheckResults]
      
      errors(r) should have size 1
    }  

    "Allow execute a list of assertions" in {
      val mock = mockActor("e") 
      val p = producer("e") 
      val probe = createProbe

      mock ! GetReceivedMessages 
      expectMsg(ReceivedMessages(List.empty))
      
      p ! "Hello Andreas"
      probe.expectMsg(MockMessageReceived("direct-vm:e"))

      mock ! CheckAssertions(expectedMessageCount(1))

      val r = receiveN(1).toList.head.asInstanceOf[CheckResults]
      
      errors(r) should be (List.empty)
        
    }  
  }
}