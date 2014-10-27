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

import akka.actor.{ActorRef, ActorSystem, Props}
import akka.pattern.ask
import akka.testkit.TestKit
import akka.util.Timeout
import com.github.dockerjava.api.command.InspectContainerResponse
import com.typesafe.config.{Config, ConfigFactory}
import de.woq.blended.itestsupport.condition.{ConditionActor, Condition}
import de.woq.blended.itestsupport.docker._
import de.woq.blended.itestsupport.docker.protocol._
import de.woq.blended.itestsupport.protocol._
import org.scalatest.{BeforeAndAfterAll, Matchers, Suite}

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

class TestContainerManager extends ContainerManager with DockerClientProvider {
  override def getClient = {
    implicit val logger = context.system.log
    DockerClientFactory(context.system.settings.config)
  }
}

trait BlendedIntegrationTestSupport
  extends Suite
  with Matchers
  with BeforeAndAfterAll { this: TestKit =>

  import de.woq.blended.itestsupport.condition.ConditionProvider._

  implicit val system: ActorSystem
  private val mgrName = "ContainerManager"

  def preCondition : Condition = alwaysTrue()

  def postCondition : Condition = alwaysTrue()

  def startContainer(timeout : FiniteDuration) = {

    implicit val eCtxt = system.dispatcher

    val mgr = system.actorOf(Props[TestContainerManager], mgrName)

    val call = (mgr ? StartContainerManager)(new Timeout(timeout))
    Await.result(call, timeout)
  }

  def stopContainer(timeout : FiniteDuration) = {
    implicit val eCtxt = system.dispatcher

    val call = (containerMgr ? StopContainerManager)(new Timeout(timeout))
    Await.result(call, timeout)
  }

  override protected def beforeAll() {
    startContainer(30.seconds) should be (ContainerManagerStarted)
    assertCondition(preCondition) should be (true)
  }

  override protected def afterAll() {
    assertCondition(postCondition) should be (true)
    stopContainer(30.seconds) should be (ContainerManagerStopped)
  }

  def containerMgr : ActorRef = {
    Await.result(system.actorSelection(s"/user/${mgrName}").resolveOne(1.second).mapTo[ActorRef], 3.seconds)
  }

  def jolokiaUrl(ctName : String, port: Int, path : String = "/hawtio/jolokia") : Future[Option[String]] = {

    implicit val eCtxt = system.dispatcher

    containerInfo(ctName).map { info =>
      Some(s"http://${info.getNetworkSettings.getIpAddress}:${port}${path}")
    }
  }

  def containerPort(ctName: String, portName: String) : Future[Option[Int]] = {

    implicit val eCtxt = system.dispatcher

    (containerMgr ? GetContainerPorts(ctName))(new Timeout(3.seconds))
      .mapTo[ContainerPorts].map { ctPorts =>
        ctPorts.ports.get(portName) match {
          case Some(namedPort) => Some(namedPort.sourcePort)
          case _ => None
        }
      }
  }

  def containerInfo(ctName: String) : Future[InspectContainerResponse] = {

    implicit val eCtxt = system.dispatcher

    (containerMgr ? InspectContainer(ctName))(new Timeout(3.seconds)).mapTo[InspectContainerResponse]
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

  def testProperties(configKey: String) : Config = ConfigFactory.load().getConfig(configKey)

}
