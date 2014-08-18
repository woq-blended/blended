package de.woq.blended.itestsupport

import akka.actor.{ActorRef, ActorSystem, Props}
import akka.pattern.ask
import akka.testkit.TestKit
import akka.util.Timeout
import com.typesafe.config.{ConfigFactory, Config}
import de.woq.blended.itestsupport.condition.{ConditionProvider, Condition, ConditionChecker}
import de.woq.blended.itestsupport.docker._
import de.woq.blended.itestsupport.docker.protocol._
import de.woq.blended.itestsupport.protocol._

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

class TestContainerManager extends ContainerManager with DockerClientProvider {
  override def getClient = {
    implicit val logger = context.system.log
    DockerClientFactory(context.system.settings.config)
  }
}

trait BlendedIntegrationTestSupport { this: TestKit =>

  import ConditionProvider._

  implicit val system: ActorSystem
  private val mgrName = "ContainerManager"

  def preCondition : Condition = alwaysTrue()

  def postCondition : Condition = alwaysTrue()

  def startContainer(timeout : FiniteDuration) = {

    implicit val eCtxt = system.dispatcher

    System.setProperty("docker.io.version", "1.12")
    val mgr = system.actorOf(Props[TestContainerManager], mgrName)

    val call = (mgr ? StartContainerManager)(new Timeout(timeout))
    Await.result(call, timeout)
  }

  def stopContainer(timeout : FiniteDuration) = {
    implicit val eCtxt = system.dispatcher

    val call = (containerMgr ? StopContainerManager)(new Timeout(timeout))
    Await.result(call, timeout)
  }

  def containerMgr : ActorRef = {
    Await.result(system.actorSelection(s"/user/${mgrName}").resolveOne(1.second).mapTo[ActorRef], 3.seconds)
  }

  def jolokiaUrl(ctName : String) : Future[Option[String]] = {

    implicit val eCtxt = system.dispatcher

    containerPort(ctName, "http").map {
      case Some(port) => Some(s"http://localhost:${port}/hawtio/jolokia")
      case _ => None
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

  def assertCondition(condition: Condition) : Boolean = {

    implicit val eCtxt = system.dispatcher

    val checker = system.actorOf(Props(ConditionChecker(condition)))

    val checkFuture = (checker ? CheckCondition)(condition.timeout).map { result =>
      result match {
        case ConditionSatisfied(_) => true
        case _ => false
      }
    }

    Await.result(checkFuture, condition.timeout)
  }

  def testProperties(configKey: String) : Config = ConfigFactory.load().getConfig(configKey)

}
