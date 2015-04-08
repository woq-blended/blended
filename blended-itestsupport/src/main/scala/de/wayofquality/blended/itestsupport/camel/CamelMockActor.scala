package de.wayofquality.blended.itestsupport.camel

import akka.camel.Consumer
import akka.camel.CamelMessage
import akka.event.LoggingReceive
import de.wayofquality.blended.itestsupport.camel.protocol._
import akka.actor.ActorLogging

object CamelMockActor {
  def apply(uri: String) = new CamelMockActor(uri)
}

class CamelMockActor(uri: String) extends Consumer with ActorLogging {
  
  def endpointUri: String = uri

  def receive = receiving()
  
  def receiving(messages: List[CamelMessage] = List.empty) : Receive = LoggingReceive {
    
    case msg : CamelMessage => 
      context.become(receiving(msg :: messages))
      context.system.eventStream.publish(MockMessageReceived(uri))
    
    case GetReceivedMessages => sender ! ReceivedMessages(messages)
    
    case ca : CheckAssertions => 
      val results = CheckResults(ca.assertions.toList.map { a => a(messages) })
      errors(results) match {
        case e if e.isEmpty => 
        case l => log.error(prettyPrint(l))
      }
      sender ! results
  }
  
  private[this] def prettyPrint(errors : List[String]) : String = 
    errors match {
      case e if e.isEmpty => s"All assertions were satisfied for mock actor [$uri]"
      case l => l.map(msg => s"  $msg").mkString(s"\n----------\nGot Assertion errors for mock actor [$uri]:\n", "\n", "\n----------")
    }
  
    
  private[this] def errors(r : CheckResults) : List[String] = r.results.collect {
    case Left(t) => t.getMessage 
  }

}