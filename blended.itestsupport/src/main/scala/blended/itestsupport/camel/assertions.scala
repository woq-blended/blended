package blended.itestsupport.camel

import akka.actor.ActorRef
import akka.camel.CamelMessage
import akka.pattern.ask
import akka.util.Timeout
import blended.itestsupport.camel.protocol.CheckAssertions
import blended.itestsupport.camel.protocol.CheckResults
import blended.itestsupport.camel.protocol.MockAssertion
import org.slf4j.LoggerFactory

import scala.collection.JavaConverters._
import scala.concurrent.Await

object MockAssertions {

  private[this] val log = LoggerFactory.getLogger(classOf[MockAssertions.type])
  
  def checkAssertions(mock: ActorRef, assertions: MockAssertion*)(implicit timeout: Timeout) : List[Throwable] = {
    val f = (mock ? CheckAssertions(assertions)).mapTo[CheckResults]
    Await.result(f, timeout.duration).results.filter { _.isLeft }.map{ _.left.get }
  }

  def minMessageCount(n: Int) : MockAssertion = { l : List[CamelMessage] =>
    if (l.size >= n) {
      Right(s"MockActor has [${l.size}] messages")
    } else {
      Left(new Exception(s"MockActor has [${l.size}] messages, but expected at least [$n] messages"))
    }
  }

  def expectedMessageCount(n: Int) : MockAssertion = { l : List[CamelMessage] => 
    l.size match {
      case s : Int if s == n => Right(s"MockActor has [$n] messages.")
      case f => Left(new Exception(s"MockActor has [$f] messages, but expected [$n] messages"))
    }
  }
  
  def expectedBodies(bodies: Any*) : MockAssertion = { l: List[CamelMessage] =>

    def compareBodies(matchList: Map[Any, Any]) : Either[Throwable, String] =
      matchList.filter { case (expected, actual) =>
        if (expected.isInstanceOf[Array[Byte]] && (actual.isInstanceOf[Array[Byte]])) 
          !expected.asInstanceOf[Array[Byte]].toList.equals(actual.asInstanceOf[Array[Byte]].toList)
        else 
          !expected.equals(actual)
      } match {
        case e if e.isEmpty => Right("MockActor has received the correct bodies")
        case l =>
          val msg = l.map { case (e, a) => s"[$e != $a]"} mkString (",")
          Left(new Exception(s"Unexpected Bodies: $msg"))
      }
    
    if (bodies.length == 1) 
      compareBodies( l.map( m => (bodies(0), m.body)).toMap )
    else
      l.size match {
        case n if n == bodies.length =>
          compareBodies(bodies.toList.zip(l.map { _.body }).toMap)
        case _ => Left(new Exception(s"The number of messages received [${l.size}] does not match the number of bodies [${bodies.length}]"))
      }
    }
  
  def expectedHeaders(headers : Map[String, Any]*) : MockAssertion = { l: List[CamelMessage] =>

    def misMatchedHeaders(m : CamelMessage, expected: Map[String, Any]) : Map[String, Any] = {
      log.debug(s"Checking headers ${m.getHeaders.asScala}, expected: [$expected]")

      expected.filter { case (k, v) =>
        !m.headers.contains(k) || m.headers(k) != v
      }
    }
    
    def compareHeaders(matchList: Map[CamelMessage, Map[String, Any]]) : Either[Throwable, String] = {

      matchList.filter { case (m, headers) => !misMatchedHeaders(m, headers).isEmpty } match {
        case e if e.isEmpty => Right("MockActor has received the correct headers")
        case l =>
          val msg = l.map { case (m, h) =>
            val headerMsg = misMatchedHeaders(m, h).mkString(",")
            s"Message [$m] did not have headers [$headerMsg]"
          }.mkString("\n")
          Left(new Exception(msg))

      }
    }

    if (headers.length == 1) 
      compareHeaders(l.map(m => (m, headers(0))).toMap)
    else l.size match {
      case n if n == headers.length =>
        compareHeaders(l.zip(headers.toList).toMap)
      case _ =>  Left(new Exception(s"The number of messages received [${l.size}] does not match the number of header maps [${headers.length}]"))
    }
  }
}
