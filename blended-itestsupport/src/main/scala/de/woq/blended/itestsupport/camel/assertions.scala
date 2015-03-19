package de.woq.blended.itestsupport.camel

import de.woq.blended.itestsupport.camel.protocol.MockAssertion
import akka.camel.CamelMessage
import de.woq.blended.itestsupport.camel.protocol.CheckResults

object MockAssertions {
  
  def prettyPrint(errors : List[String]) : String = 
    errors match {
      case e if e.isEmpty => "All assertions were satisfied"
      case l => l.map(msg => s"  $msg").mkString("\n----------\nGot Assertion errors:\n", "\n", "\n----------")
    }
  
    
  def errors(r : CheckResults) : List[String] = r.results.collect {
    case Left(t) => t.getMessage 
  }
  
  def expectedMessageCount(n: Int) : MockAssertion = { l : List[CamelMessage] => 
    l.size match {
      case s : Int if s == n => Right(s"MockActor has [$n] messages.")
      case f => Left(new Exception(s"MockActor has [$f] messages, but expected [$n] messages"))
    }
  }
  
}