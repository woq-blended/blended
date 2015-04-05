package de.wayofquality.blended.itestsupport.camel

import de.wayofquality.blended.itestsupport.camel.protocol.MockAssertion
import akka.camel.CamelMessage
import de.wayofquality.blended.itestsupport.camel.protocol.CheckResults
import akka.actor.ActorRef
import de.wayofquality.blended.itestsupport.camel.protocol.MockAssertion
import de.wayofquality.blended.itestsupport.camel.protocol.CheckAssertions
import akka.testkit.TestKit
import scala.concurrent.Future
import akka.pattern.ask
import akka.util.Timeout
import scala.concurrent.Await
import de.wayofquality.blended.itestsupport.camel.protocol.CheckResults

object MockAssertions { 
  
  def checkAssertions(mock: ActorRef, assertions: MockAssertion*)(implicit timeout: Timeout) : List[Throwable] = {
    val f = (mock ? CheckAssertions(assertions)).mapTo[CheckResults]
    Await.result(f, timeout.duration).results.filter { _.isLeft }.map{ _.left.get }
  } 
  
  def expectedMessageCount(n: Int) : MockAssertion = { l : List[CamelMessage] => 
    l.size match {
      case s : Int if s == n => Right(s"MockActor has [$n] messages.")
      case f => Left(new Exception(s"MockActor has [$f] messages, but expected [$n] messages"))
    }
  }
  
  def expectedBodies(bodies: String*) : MockAssertion = { l: List[CamelMessage] =>
    l.size match {
      case n if n == bodies.length => 
        val matchList = bodies.toList.zip(l.map { _.body }).toMap
        val errors = matchList.filter { case (expected, actual) => expected != actual }
        errors match {
          case e if e.isEmpty => Right("MockActor has received the correct bodies")
          case l => 
            val msg = l.map { case (e,a) => s"[$e != $a]" } mkString (",")
            Left(new Exception(s"Unexpected Bodies: $msg"))
        }
        
      case _ => Left(new Exception(s"The number of messages received [${l.size}] does not match the number of bodies [${bodies.length}]"))
    }
  }
  
}