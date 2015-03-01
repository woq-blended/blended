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

import akka.actor.Props
import akka.testkit.{TestProbe, TestKit}
import de.woq.blended.itestsupport.docker.DockerContainerProxy
import de.woq.blended.itestsupport.docker.protocol._
import org.scalatest.{BeforeAndAfterAll, Matchers, Suite}

trait BlendedIntegrationTestSupport
  extends Suite
  with Matchers
  with BeforeAndAfterAll { this: TestKit =>
  
  val dockerProxyProbe = new TestProbe(system)
  
  def dockerConnect : Unit = {
    system.eventStream.subscribe(dockerProxyProbe.ref, classOf[ContainerProxyStarted])
    system.actorOf(Props[DockerContainerProxy]) ! StartContainerProxy
    
    dockerProxyProbe.expectMsgType[ContainerProxyStarted]
  }
  
  dockerConnect

//  import de.woq.blended.itestsupport.condition.ConditionProvider._
//
//  implicit val system: ActorSystem
//  private val log = system.log
//
//  private val mgrName = "ContainerManager"
//
//  def preCondition : Condition = alwaysTrue()
//
//  def postCondition : Condition = alwaysTrue()
//
//  final def startContainer(timeout : FiniteDuration) = {
//
//    implicit val eCtxt = system.dispatcher
//
//    val mgr = system.actorOf(Props[EmbeddedContainerManager], mgrName)
//
//    val call = (mgr ? StartContainerManager)(new Timeout(timeout))
//    Await.result(call, timeout)
//  }
//
//  final def stopContainer(timeout : FiniteDuration) = {
//    implicit val eCtxt = system.dispatcher
//
//    val call = (containerMgr ? StopContainerManager)(new Timeout(timeout))
//    Await.result(call, timeout)
//  }
//
//  final def beforeSuite() : Unit = {
//    log.info("Preparing Test Suite ...")
//    startContainer(30.seconds) should be (ContainerManagerStarted)
//    log.info(s"Verifying precondition [$preCondition]")
//    assertCondition(preCondition) should be (true)
//    log.info("Initializing BlendedTestContext ...")
//    initTestContext()
//  }
//
//  final def afterSuite() : Unit = {
//    log.info(s"Verifying postcondition [${postCondition}]")
//    assertCondition(postCondition) should be (true)
//    log.info("Shutting down Test Suite ...")
//    stopContainer(30.seconds) should be (ContainerManagerStopped)
//  }
//
//  override protected def afterAll(): Unit = afterSuite()
//
//  def initTestContext() : Unit = {}
//
//  final def containerMgr : ActorRef = {
//    Await.result(system.actorSelection(s"/user/$mgrName").resolveOne(1.second).mapTo[ActorRef], 3.seconds)
//  }
//
//  def jolokiaUrl(ctName : String, port: Int, path : String = "/hawtio/jolokia") : Future[Option[String]] = {
//
//    implicit val eCtxt = system.dispatcher
//
//    containerInfo(ctName).map { info =>
//      Some(s"http://${info.getNetworkSettings.getIpAddress}:${port}${path}")
//    }
//  }
//
//  def containerPort(ctName: String, portName: String) : Future[Option[Int]] = {
//
//    implicit val eCtxt = system.dispatcher
//
//    (containerMgr ? GetContainerPorts(ctName))(new Timeout(3.seconds))
//      .mapTo[ContainerPorts].map { ctPorts =>
//        ctPorts.ports.get(portName) match {
//          case Some(namedPort) => Some(namedPort.privatePort)
//          case _ => None
//        }
//      }
//  }
//
//  def containerInfo(ctName: String) : Future[InspectContainerResponse] = {
//    implicit val eCtxt = system.dispatcher
//    (containerMgr ? InspectContainer(ctName))(new Timeout(3.seconds)).mapTo[InspectContainerResponse]
//  }
//
//  def assertCondition(condition: Condition) : Boolean = {
//
//    implicit val eCtxt = system.dispatcher
//
//    val checker = system.actorOf(Props(ConditionActor(condition)))
//
//    val checkFuture = (checker ? CheckCondition)(condition.timeout).map { result =>
//      result match {
//        case cr: ConditionCheckResult => cr.allSatisfied
//        case _ => false
//      }
//    }
//
//    Await.result(checkFuture, condition.timeout)
//  }
//
//  def testProperties(configKey: String) : Config = ConfigFactory.load().getConfig(configKey)
}
