package blended.streams.testsupport

import akka.util.ByteString
import blended.streams.message._
import blended.util.logging.Logger

import scala.util.Try

object FlowMessageAssertion {

  def checkAssertions(envelopes : FlowEnvelope*)(assertions: FlowMessageAssertion*) : Seq[String] = {
    assertions.map(a => a.f(envelopes))
      .filter(_.isFailure)
      .map(_.failed.get.getMessage())
  }
}

trait FlowMessageAssertion {
  def f : Seq[FlowEnvelope] => Try[String]
}

object ExpectedMessageCount {
  def apply(count : Int) : ExpectedMessageCount = new ExpectedMessageCount(count)
}

class ExpectedMessageCount(count : Int) extends FlowMessageAssertion {
  override def f : Seq[FlowEnvelope] => Try[String] = l => Try {
    if (l.lengthCompare(count) == 0) {
      s"MockActor has [${l.size}] messages."
    } else {
      throw new Exception(s"MockActor has [${l.size}] messages, but expected [$count] messages")
    }
  }
}

object MinMessageCount {
  def apply(count : Int) : MinMessageCount = new MinMessageCount(count)
}

class MinMessageCount(count : Int) extends FlowMessageAssertion {
  override def f: Seq[FlowEnvelope] => Try[String] = l => Try{
    if (l.size >= count) {
      s"MockActor has [${l.size}] messages"
    } else {
      throw new Exception(s"MockActor has [${l.size}] messages, but expected at least [$count] messages")
    }
  }
}

object ExpectedBodies {
  def apply(bodies: Option[Any]*): ExpectedBodies = new ExpectedBodies(bodies:_*)
}

class ExpectedBodies(bodies: Option[Any]*) extends FlowMessageAssertion {

  private val matchBody : FlowMessage => Option[Any] => Boolean = msg => expected =>
    msg match {
      case txtMsg : TextFlowMessage => expected.isDefined && expected.forall(_.toString.equals(txtMsg.getText()))
      case binMsg : BinaryFlowMessage => expected.isDefined && { expected.forall { c => c match {
        case byteString: ByteString => byteString.equals(binMsg.content)
        case byteArr: Array[Byte] => ByteString(byteArr).equals(binMsg.content)
        case _ => false
      }}}
      case baseMsg : BaseFlowMessage => expected.isEmpty
    }

  override def f: Seq[FlowEnvelope] => Try[String] = l => {

    def compareBodies(matchList: Map[Option[Any], FlowMessage]) : Try[String] = Try {
      matchList.filter { case (expected, actual) => matchBody(actual)(expected) } match {
        case s if s.isEmpty => "MockActor has received the correct bodies"
        case e =>
          val msg = e.map { case (b, a) => s"[$b != $a]"} mkString (",")
          throw new Exception(s"Unexpected Bodies: $msg")
      }
    }

    if (bodies.length == 1) {
      val compMap : Map[Option[Any], FlowMessage] = l.map { m => (bodies(0),  m.flowMessage ) }.toMap
      compareBodies( compMap )
    }
    else {
      l.size match {
        case n if n == bodies.length =>
          compareBodies(bodies.toList.zip(l.map { _.flowMessage }).toMap)
        case _ => throw new Exception(s"The number of messages received [${l.size}] does not match the number of bodies [${bodies.length}]")
      }
    }
  }
}

object MandatoryHeaders {
  def apply(header: List[String]): MandatoryHeaders = new MandatoryHeaders(header)
}

class MandatoryHeaders(header: List[String]) extends FlowMessageAssertion {
  override def f: Seq[FlowEnvelope] => Try[String] = l => Try {

    l.filter { m =>
      val missing = header.filter { h => m.flowMessage.header.get(h).isEmpty }

      if (missing.nonEmpty) {
        throw new Exception(s"Missing headers ${missing.mkString("[", ",", "]")}")
      }

      true
    }

    "Mandatory header present"
  }
}

object ExpectedHeaders {
  def apply(headers: (String, Any)*): ExpectedHeaders = new ExpectedHeaders(headers.toMap)
}

class ExpectedHeaders(headers : Map[String, Any]*) extends FlowMessageAssertion {

  private[this] val log = Logger[ExpectedHeaders]

  override def f: Seq[FlowEnvelope] => Try[String] = l => Try {

    def misMatchedHeaders(m : FlowEnvelope, expected: Map[String, Any]) : Map[String, Any] = {

      val msgHeader = m.flowMessage.header

      log.info(s"Checking headers [$msgHeader], expected: [$expected]")

      expected.filter { case (k, v) =>
        !msgHeader.contains(k) || !msgHeader.get(k).forall(value => value.value == v)
      }
    }

    def compareHeaders(matchList: Map[FlowEnvelope, Map[String, Any]]) : Try[String] = Try {

      matchList.filter { case (m, header) => misMatchedHeaders(m, header).nonEmpty } match {
        case e if e.isEmpty => s"MockActor has received the correct headers"
        case mismatch =>
          val msg = mismatch.map { case (m, h) =>
            val headerMsg = misMatchedHeaders(m, h).mkString(",")
            s"Message [$m] did not have headers [$headerMsg]"
          }.mkString("\n")
          throw new Exception(msg)
      }
    }

    if (headers.length == 1) {
      compareHeaders(l.map(m => (m, headers(0))).toMap).get
    } else {
      l.size match {
        case n if n == headers.length =>
          compareHeaders(l.zip(headers.toList).toMap).get
        case _ =>
          throw new Exception(s"The number of messages received [${l.size}] does not match the number of header maps [${headers.length}]")
      }
    }
  }
}

