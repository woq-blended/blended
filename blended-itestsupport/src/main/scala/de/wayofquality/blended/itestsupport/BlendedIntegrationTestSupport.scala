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

package de.wayofquality.blended.itestsupport

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
import de.wayofquality.blended.itestsupport.camel.protocol.MockMessageReceived
import de.wayofquality.blended.itestsupport.condition.Condition
import de.wayofquality.blended.itestsupport.condition.ConditionActor
import de.wayofquality.blended.itestsupport.protocol._
import de.wayofquality.blended.itestsupport.condition.ConditionProvider
import akka.testkit.ImplicitSender
import scala.concurrent.duration.FiniteDuration
import de.wayofquality.blended.itestsupport.docker.protocol._
import scala.concurrent.duration._

trait BlendedIntegrationTestSupport { 
  
  val ctProxyName = "ContainerProxy"
  val ctProxyPath = s"/user/$ctProxyName"
  
  def actorByName(name: String)(implicit testKit: TestKit) : ActorRef = {    
    implicit val timeout = Timeout(3.seconds)
    Await.result(testKit.system.actorSelection(name).resolveOne(), 3.seconds)
  }
  
  def testContext(implicit timeout: FiniteDuration, testKit: TestKit) : CamelContext = {
    val ctProxy = actorByName(ctProxyPath)
    val probe = new TestProbe(testKit.system)
    val cuts = ContainerUnderTest.containerMap(testKit.system.settings.config)    
    ctProxy.tell(TestContextRequest(cuts), probe.ref)
    probe.receiveN(1,timeout).head.asInstanceOf[CamelContext] 
  }
  
  def containerReady(implicit timeout: FiniteDuration, testKit : TestKit) : Unit = {
    val ctProxy = actorByName(ctProxyPath)
    val probe = new TestProbe(testKit.system)
    ctProxy.tell(ContainerReady_?, probe.ref)
    probe.expectMsg(timeout, ContainerReady(true))
  } 
  
  def stopContainers(implicit timeout: FiniteDuration, testKit: TestKit) : Unit = {
    val ctProxy = actorByName(ctProxyPath)
    val probe = new TestProbe(testKit.system)
    ctProxy.tell(StopContainerManager, probe.ref)
    probe.expectMsg(timeout, ContainerManagerStopped)
  }
  
  def assertCondition(condition: Condition)(implicit testKit: TestKit) : Boolean = {

    implicit val eCtxt = testKit.system.dispatcher

    val checker = testKit.system.actorOf(Props(ConditionActor(condition)))

    val checkFuture = (checker ? CheckCondition)(condition.timeout).map { result =>
      result match {
        case cr: ConditionCheckResult => cr.allSatisfied
        case _ => false
      }
    }

    Await.result(checkFuture, condition.timeout)
  }
}
