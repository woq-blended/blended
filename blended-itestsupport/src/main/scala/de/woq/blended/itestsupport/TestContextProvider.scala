/*
 * Copyright 2014ff, WoQ - Way of Quality GmbH
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

package de.woq.blended.itestsupport

import akka.actor.Actor
import akka.actor.ActorLogging
import akka.event.LoggingReceive
import de.woq.blended.itestsupport.protocol._
import akka.camel.CamelExtension
import org.apache.camel.CamelContext
import de.woq.blended.itestsupport.condition.Condition
import de.woq.blended.itestsupport.condition.ConditionProvider._

trait TestContextConfigurator {
  def configure(cuts : Map[String, ContainerUnderTest], context: CamelContext) : CamelContext
}

class TestContextCreator extends Actor with ActorLogging { this : TestContextConfigurator =>
  
  val camel = CamelExtension(context.system)
  
  def receive = LoggingReceive {
    case r : TestContextRequest => 
      log info s"Creating TestCamelContext for CUT's [${r.cuts}]"
      
      val result = try 
        Right(configure(r.cuts, camel.context)) 
      catch {
        case t : Throwable => Left(t)
      }
    
      log debug s"Created TestCamelContext [$result]"
      
      sender ! TestContextResponse(result)
      context.stop(self)
  }
  
}