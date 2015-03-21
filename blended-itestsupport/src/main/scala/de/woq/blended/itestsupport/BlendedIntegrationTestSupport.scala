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

import scala.collection.convert.Wrappers.JListWrapper
import scala.concurrent.Await
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

import org.apache.camel.CamelContext
import org.scalatest.Matchers

import akka.actor.ActorRef
import akka.actor.ActorSystem
import akka.actor.Props
import akka.camel.CamelExtension
import akka.pattern.ask
import akka.testkit.TestKit
import akka.testkit.TestProbe
import akka.util.Timeout
import akka.util.Timeout.durationToTimeout
import de.woq.blended.itestsupport.camel.protocol.MockMessageReceived
import de.woq.blended.itestsupport.condition.Condition
import de.woq.blended.itestsupport.condition.ConditionActor
import de.woq.blended.itestsupport.protocol._

trait BlendedIntegrationTestSupport
  extends Matchers { this: TestKit =>
    
  implicit val system: ActorSystem 
  private[this] val log = system.log
  
  lazy val camel = CamelExtension(system)
  lazy val mockProbe = new TestProbe(system)
  system.eventStream.subscribe(mockProbe.ref, classOf[MockMessageReceived])

  def testContext(ctProxy : ActorRef) : Future[CamelContext] = {
  
    implicit val timeout = Timeout(1200.seconds)
    
    val cuts = ContainerUnderTest.containerMap(system.settings.config)
    (ctProxy ? TestContextRequest(cuts)).mapTo[CamelContext] 
  }
  
  def assertCondition(condition: Condition) : Boolean = {

    implicit val eCtxt = system.dispatcher

    val checker = system.actorOf(Props(ConditionActor(condition)))

    val checkFuture = (checker ? CheckCondition)(condition.timeout).map { result =>
      result match {
        case cr: ConditionCheckResult => cr.allSatisfied
        case _ => false
      }
    }

    Await.result(checkFuture, condition.timeout)
  }
}
