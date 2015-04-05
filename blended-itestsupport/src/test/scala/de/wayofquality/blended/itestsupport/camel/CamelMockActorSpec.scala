package de.wayofquality.blended.itestsupport.camel

import akka.camel.Consumer
import akka.camel.CamelMessage
import akka.testkit.TestActors
import org.scalatest.Matchers
import akka.camel.CamelExtension
import akka.actor.ActorSystem
import de.wayofquality.blended.testsupport.TestActorSys
import org.scalatest.WordSpecLike
import de.wayofquality.blended.itestsupport.docker.DockerTestSetup
import org.scalatest.mock.MockitoSugar
import akka.testkit.TestActorRef
import akka.actor.Props
import de.wayofquality.blended.itestsupport.camel.protocol._
import akka.camel.Producer
import akka.camel.Oneway
import akka.testkit.TestProbe
import de.wayofquality.blended.itestsupport.camel.MockAssertions._
import akka.testkit.TestKit
import scala.concurrent.Await
import scala.concurrent.duration._
import akka.util.Timeout

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