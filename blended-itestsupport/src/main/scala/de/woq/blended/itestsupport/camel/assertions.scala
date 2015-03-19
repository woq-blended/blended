package de.woq.blended.itestsupport.camel

import de.woq.blended.itestsupport.camel.protocol.MockAssertion
import akka.camel.CamelMessage
import de.woq.blended.itestsupport.camel.protocol.CheckResults

object MockAssertions {
  
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