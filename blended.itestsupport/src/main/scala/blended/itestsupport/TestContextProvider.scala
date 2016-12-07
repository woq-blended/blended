package blended.itestsupport

import akka.actor.{Actor, ActorLogging}
import akka.camel.CamelExtension
import akka.event.LoggingReceive
import blended.itestsupport.protocol._
import org.apache.camel.CamelContext

trait TestContextConfigurator {
  def configure(cuts : Map[String, ContainerUnderTest], context: CamelContext) : CamelContext
}

class TestContextCreator extends Actor with ActorLogging { this : TestContextConfigurator =>
  
  val camel = CamelExtension(context.system)
  
  def receive = LoggingReceive {
    case r : TestContextRequest => 
      log info s"Creating TestCamelContext for CUT's [${r.cuts}]"
      
      val result = try 
        Right(configure(r.cuts, camel.context)) 
      catch {
        case t : Throwable => Left(t)
      }
    
      log debug s"Created TestCamelContext [$result]"
      
      sender ! TestContextResponse(result)
      context.stop(self)
  }
  
}