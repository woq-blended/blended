/*
 * Copyright 2014ff,  https://github.com/woq-blended
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package blended.itestsupport.camel

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