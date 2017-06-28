package blended.itestsupport

import java.io.{ByteArrayOutputStream, File, FileOutputStream}

import akka.actor.{ActorRef, Props}
import akka.pattern.ask
import akka.testkit.{TestKit, TestProbe}
import akka.util.Timeout
import akka.util.Timeout.durationToTimeout
import blended.itestsupport.compress.TarFileSupport
import blended.itestsupport.condition.{Condition, ConditionActor}
import blended.itestsupport.docker.protocol._
import blended.itestsupport.protocol._
import org.apache.camel.CamelContext
import org.slf4j.LoggerFactory

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{Await, Future}

trait BlendedIntegrationTestSupport {

  private[this] val logger = LoggerFactory.getLogger(classOf[BlendedIntegrationTestSupport])

  val testOutput = System.getProperty("projectTestOutput", "")

  def testContext(ctProxy: ActorRef)(implicit timeout: Timeout, testKit: TestKit) : CamelContext = {
    val probe = new TestProbe(testKit.system)
    val cuts = ContainerUnderTest.containerMap(testKit.system.settings.config)
    ctProxy.tell(TestContextRequest(cuts), probe.ref)
    probe.receiveN(1,timeout.duration).head.asInstanceOf[CamelContext]
  }
  
  def containerReady(ctProxy: ActorRef)(implicit timeout: Timeout, testKit : TestKit) : Unit = {
    val probe = new TestProbe(testKit.system)
    ctProxy.tell(ContainerReady_?, probe.ref)
    probe.expectMsg(timeout.duration, ContainerReady(true))
  }
  
  def stopContainers(ctProxy: ActorRef)(implicit timeout: Timeout, testKit: TestKit) : Unit = {
    val probe = new TestProbe(testKit.system)
    testKit.system.log.debug(s"stopProbe [${probe.ref}]")
    ctProxy.tell(new StopContainerManager(timeout.duration), probe.ref)
    probe.expectMsg(timeout.duration, ContainerManagerStopped)
  }

  def writeContainerDirectory(
    ctProxy : ActorRef,
    ctName: String,
    target: String,
    file: File,
    user : Int = 0,
    group : Int = 0
  )(implicit timeout: Timeout, testKit: TestKit) : Future[WriteContainerDirectoryResult] = {

    logger.info(s"Writing directory [${file.getAbsolutePath()}] to [$ctName:$target]")
    implicit val eCtxt = testKit.system.dispatcher

    val bos = new ByteArrayOutputStream()
    TarFileSupport.tar(file, bos, user, group)

    ctProxy.ask(ConfiguredContainer_?(ctName)).mapTo[ConfiguredContainer].flatMap { cc =>
      cc.cut match {
        case None => Future(WriteContainerDirectoryResult(Left(new Exception(s"Container with name [$ctName] not found."))))
        case Some(cut) => ctProxy.ask(WriteContainerDirectory(cut, target, bos.toByteArray())).mapTo[WriteContainerDirectoryResult]
      }
    }
  }

  def readContainerDirectory(ctProxy: ActorRef, ctName: String, dirName: String)(implicit timeout: Timeout, testKit: TestKit) : Future[GetContainerDirectoryResult] = {

    logger.info(s"Reading container directory [$ctName:$dirName]")
    implicit val eCtxt = testKit.system.dispatcher

    ctProxy.ask(ConfiguredContainer_?(ctName)).mapTo[ConfiguredContainer].flatMap { cc =>
      cc.cut match {
        case None => throw new Exception(s"Container with name [$ctName] not found.")
        case Some(cut) => ctProxy.ask(GetContainerDirectory(cut, dirName)).mapTo[GetContainerDirectoryResult]
      }
    }
  }

  def saveContainerDirectory(baseDir: String, dir: ContainerDirectory) : Unit = {
    dir.content.foreach { case (name, content) =>
      val file = new File(s"$baseDir/$name")
      file.getParentFile().mkdirs()

      if (content.size > 0) {
        val fos = new FileOutputStream(file)
        fos.write(content)
        fos.flush()
        fos.close()
      }
    }
  }

  def execContainerCommand(
    ctProxy: ActorRef, ctName: String, cmdTimeout: FiniteDuration, user: String, cmd: String*
  )(implicit timeout: Timeout, testKit: TestKit) : Future[ExecuteContainerCommandResult] = {

    implicit val eCtxt = testKit.system.dispatcher

    ctProxy.ask(ConfiguredContainer_?(ctName)).mapTo[ConfiguredContainer].flatMap { cc =>
      cc.cut match {
        case None => Future(ExecuteContainerCommandResult(Left(new Exception(s"Container with name [$ctName] not found."))))
        case Some(cut) =>
          ctProxy.ask(ExecuteContainerCommand(cut, cmdTimeout, user, cmd:_*))(cmdTimeout).mapTo[ExecuteContainerCommandResult]
      }
    }
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
