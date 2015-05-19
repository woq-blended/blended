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

import akka.actor.ActorLogging
import akka.camel.{Ack, CamelMessage, Consumer}
import akka.event.LoggingReceive
import blended.itestsupport.camel.protocol._

object CamelMockActor {
  def apply(uri: String, ack: Boolean = true) = new CamelMockActor(uri)
}

class CamelMockActor(uri: String, ack: Boolean = true) extends Consumer with ActorLogging {
  
  override def endpointUri: String = uri

  override def receive = receiving()
  
  override def autoAck = false
  
  def receiving(messages: List[CamelMessage] = List.empty) : Receive = LoggingReceive {
    
    case msg : CamelMessage => 
      context.become(receiving(msg :: messages))
      context.system.eventStream.publish(MockMessageReceived(uri))
      if (ack) sender ! Ack
    
    case GetReceivedMessages => sender ! ReceivedMessages(messages)
    
    case ca : CheckAssertions => 
      val results = CheckResults(ca.assertions.toList.map { a => a(messages) })
      errors(results) match {
        case e if e.isEmpty => 
        case l => log.error(prettyPrint(l))
      }
      sender ! results
  }
  
  private[this] def prettyPrint(errors : List[String]) : String = 
    errors match {
      case e if e.isEmpty => s"All assertions were satisfied for mock actor [$uri]"
      case l => l.map(msg => s"  $msg").mkString(s"\n----------\nGot Assertion errors for mock actor [$uri]:\n", "\n", "\n----------")
    }
  
    
  private[this] def errors(r : CheckResults) : List[String] = r.results.collect {
    case Left(t) => t.getMessage 
  }

}