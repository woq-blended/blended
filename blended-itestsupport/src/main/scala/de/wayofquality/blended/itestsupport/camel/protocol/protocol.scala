package de.wayofquality.blended.itestsupport.camel

import akka.camel.CamelMessage

package object protocol {

  type AssertionResult = Either[Throwable, String]
  type MockAssertion = List[CamelMessage] => AssertionResult
  
  case object GetReceivedMessages
  case class  ReceivedMessages(messages: List[CamelMessage])
  case object ResetMessages
  case class  MockMessageReceived(uri: String)
  case class  CheckAssertions(assertions : Seq[MockAssertion])
  case class  CheckResults(results: List[AssertionResult])

}