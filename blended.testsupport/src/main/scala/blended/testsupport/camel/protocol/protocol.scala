package blended.testsupport.camel

import akka.camel.CamelMessage

import scala.util.Try

package protocol {
  case class MockActorReady(uri : String)
  case object GetReceivedMessages
  case class ReceivedMessages(messages : List[CamelMessage])
  case object ResetMessages
  case class MockMessageReceived(uri : String, msg : CamelMessage)
  case class CheckAssertions(assertions : Seq[MockAssertion])
  case class CheckResults(results : List[Try[String]])
  case object StopReceive
  case class ReceiveStopped(uri : String)
}
