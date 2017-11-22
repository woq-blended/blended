package blended.itestsupport.camel

import akka.actor.{ActorSystem, Props}
import akka.camel.{Oneway, Producer}
import akka.testkit.{TestActorRef, TestProbe}
import akka.util.Timeout
import blended.testsupport.camel.protocol._
import blended.itestsupport.docker.DockerTestSetup
import blended.testsupport.TestActorSys
import blended.testsupport.camel._
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{Matchers, WordSpec}

import scala.concurrent.duration._

class CamelMockActorSpec extends WordSpec
  with Matchers
  with DockerTestSetup
  with MockitoSugar {

  implicit val timeout = Timeout(3.seconds)
  
  def mockActor(uri: String)(implicit system : ActorSystem) = TestActorRef(Props(CamelMockActor(s"direct-vm:$uri")))
  
  def producer(uri: String)(implicit system : ActorSystem) = TestActorRef(Props(new Producer with Oneway {
    def endpointUri = s"direct-vm:$uri?block=true"    
  })) 
  
  def createProbe()(implicit system : ActorSystem) : TestProbe = {
    val probe = TestProbe()
    system.eventStream.subscribe(probe.ref, classOf[MockMessageReceived])
    probe
  } 

  "The Camel Mock Actor" should {
    
    "Initialze with an empty set of received messages" in TestActorSys { testkit =>
      implicit val system = testkit.system
      val probe = TestProbe()

      val mock = mockActor("a")
      
      mock.tell(GetReceivedMessages, probe.ref)
      probe.expectMsg(ReceivedMessages(List.empty))
    }
    
    "Should notify upon reception of a message on the event bus" in TestActorSys { testkit =>
      implicit val system = testkit.system

      val mock = mockActor("b") 
      val p = producer("b")
      
      val probe = createProbe()

      p ! "Hello Andreas"
      probe.expectMsgType[MockMessageReceived]
    }
  
    "Track the received messages" in TestActorSys { testkit =>
      implicit val system = testkit.system
      val sender = TestProbe()

      val mock = mockActor("c") 
      val p = producer("c") 
      val probe = createProbe

      mock.tell(GetReceivedMessages, sender.ref)
      sender.expectMsg(ReceivedMessages(List.empty))
      
      p ! "Hello Andreas"
      probe.expectMsgType[MockMessageReceived]

      mock.tell(GetReceivedMessages, sender.ref)
      sender.fishForMessage() {
        case list : ReceivedMessages => 
          list.messages.size == 1
        case _ => false
      }
    }

    "Allow execute a list of assertions and collect messages for failed assertions" in TestActorSys { testkit =>
      implicit val system = testkit.system
      val sender = TestProbe()

      val mock = mockActor("d") 
      val p = producer("d") 
      val probe = createProbe

      mock.tell(GetReceivedMessages, sender.ref)
      sender.expectMsg(ReceivedMessages(List.empty))
      
      p ! "Hello Andreas"
      probe.expectMsgType[MockMessageReceived]

      MockAssertion.checkAssertions(mock, ExpectedMessageCount(2)) should have size 1
    }  

    "Allow execute a list of assertions" in TestActorSys { testkit =>
      implicit val system = testkit.system
      val sender = TestProbe()

      val mock = mockActor("e") 
      val p = producer("e") 
      val probe = createProbe

      mock.tell(GetReceivedMessages, sender.ref)
      sender.expectMsg(ReceivedMessages(List.empty))
      
      p ! "Hello Andreas"
      probe.expectMsgType[MockMessageReceived]

      MockAssertion.checkAssertions(mock, ExpectedMessageCount(1)) should have size 0
    }  
  }
}
