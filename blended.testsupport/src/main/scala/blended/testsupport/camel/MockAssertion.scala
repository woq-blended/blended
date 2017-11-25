package blended.testsupport.camel

import akka.actor.ActorRef
import akka.camel.CamelMessage
import akka.util.Timeout
import blended.testsupport.camel.protocol.{CheckAssertions, CheckResults}
import org.slf4j.LoggerFactory

import scala.util.Try
import scala.collection.JavaConverters._
import scala.concurrent.Await
import akka.pattern.ask

object MockAssertion {

  def checkAssertions(mock: ActorRef, assertions: MockAssertion*)(implicit timeout: Timeout) : List[Throwable] = {
    val f = (mock ? CheckAssertions(assertions)).mapTo[CheckResults]
    Await.result(f, timeout.duration).results.filter(_.isFailure).map(_.failed.get)
  }
}

trait MockAssertion {
  def f : List[CamelMessage] => Try[String]
}

case class ExpectedMessageCount(count : Int) extends MockAssertion {
  override def f = l => Try {
    if (l.size == count)
      s"MockActor has [${l.size}] messages."
    else
      throw new Exception(s"MockActor has [${l.size}] messages, but expected [$count] messages")
  }
}

case class MinMessageCount(count : Int) extends MockAssertion {
  override def f = l => Try{
    if (l.size >= count)
      s"MockActor has [${l.size}] messages"
    else
      throw new Exception(s"MockActor has [${l.size}] messages, but expected at least [$count] messages")
  }
}

case class ExpectedBodies(bodies: Any*) extends MockAssertion {
  override def f = l => {

    def compareBodies(matchList: Map[Any, Any]) : Try[String] = Try {
      matchList.filter { case (expected, actual) =>
        if (expected.isInstanceOf[Array[Byte]] && (actual.isInstanceOf[Array[Byte]]))
          !expected.asInstanceOf[Array[Byte]].toList.equals(actual.asInstanceOf[Array[Byte]].toList)
        else
          !expected.equals(actual)
      } match {
        case e if e.isEmpty => "MockActor has received the correct bodies"
        case l =>
          val msg = l.map { case (e, a) => s"[$e != $a]"} mkString (",")
          throw new Exception(s"Unexpected Bodies: $msg")
      }
    }

    if (bodies.length == 1)
      compareBodies( l.map( m => (bodies(0), m.body)).toMap )
    else
      l.size match {
        case n if n == bodies.length =>
          compareBodies(bodies.toList.zip(l.map { _.body }).toMap)
        case _ => throw new Exception(s"The number of messages received [${l.size}] does not match the number of bodies [${bodies.length}]")
      }
  }
}

case class ExpectedHeaders(headers : Map[String, Any]*) extends MockAssertion {

  private[this] val log = LoggerFactory.getLogger(classOf[ExpectedHeaders])

  private[this] def extractHeader (m : CamelMessage) : Map[String, Any] = m.getHeaders.asScala.toMap

  override def f: List[CamelMessage] => Try[String] = l => Try {

    def misMatchedHeaders(m : CamelMessage, expected: Map[String, Any]) : Map[String, Any] = {
      log.info(s"Checking headers ${extractHeader(m)}, expected: [$expected]")

      expected.filter { case (k, v) =>
        !m.headers.contains(k) || m.headers(k) != v
      }
    }

    def compareHeaders(matchList: Map[CamelMessage, Map[String, Any]]) : Try[String] = Try {

      matchList.filter { case (m, headers) => !misMatchedHeaders(m, headers).isEmpty } match {
        case e if e.isEmpty => s"MockActor has received the correct headers"
        case l =>
          val msg = l.map { case (m, h) =>
            val headerMsg = misMatchedHeaders(m, h).mkString(",")
            s"Message [$m] did not have headers [$headerMsg]"
          }.mkString("\n")
          throw new Exception(msg)
      }
    }

    if (headers.length == 1)
      compareHeaders(l.map(m => (m, headers(0))).toMap).get
    else l.size match {
      case n if n == headers.length =>
        compareHeaders(l.zip(headers.toList).toMap).get
      case _ =>  throw new Exception(s"The number of messages received [${l.size}] does not match the number of header maps [${headers.length}]")
    }
  }
}

