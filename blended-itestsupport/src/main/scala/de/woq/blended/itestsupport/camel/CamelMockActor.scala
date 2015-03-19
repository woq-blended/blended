package de.woq.blended.itestsupport.camel

import akka.camel.Consumer
import akka.camel.CamelMessage
import akka.event.LoggingReceive
import de.woq.blended.itestsupport.camel.protocol.GetReceivedMessages
import de.woq.blended.itestsupport.camel.protocol.ReceivedMessages
import de.woq.blended.itestsupport.camel.protocol.MockMessageReceived
import de.woq.blended.itestsupport.camel.protocol.CheckAssertions
import de.woq.blended.itestsupport.camel.protocol.CheckResults

object CamelMockActor {
  def apply(uri: String) = new CamelMockActor(uri)
}

class CamelMockActor(uri: String) extends Consumer {
  
  def endpointUri: String = uri

  def receive = receiving()
  
  def receiving(messages: List[CamelMessage] = List.empty) : Receive = LoggingReceive {
    
    case msg : CamelMessage => 
      context.become(receiving(msg :: messages))
      context.system.eventStream.publish(MockMessageReceived(uri))
    
    case GetReceivedMessages => sender ! ReceivedMessages(messages)
    
    case ca : CheckAssertions => 
      sender ! CheckResults(ca.assertions.toList.map { a => a(messages) })
  }  
}