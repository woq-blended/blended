package blended.itest.runner

import akka.testkit.TestKit
import akka.actor.ActorSystem
import blended.testsupport.scalatest.LoggingFreeSpecLike
import org.scalatest.matchers.should.Matchers
import org.scalatest.BeforeAndAfterAll
import scala.concurrent.Await
import scala.concurrent.duration._
import akka.testkit.TestProbe
import java.{util => ju}
import scala.util.Try
import scala.util.Success

class TestRunnerSpec extends TestKit(ActorSystem("TestRunner"))
  with LoggingFreeSpecLike
  with Matchers
  with BeforeAndAfterAll {
  
  override protected def afterAll(): Unit = {
    Await.result(system.terminate(), 10.seconds)
  }  

  private val id : String = ju.UUID.randomUUID().toString()

  private def template : TestTemplate = new TestTemplate() {
    override val name : String = "myFactory"
    override def generateId: String = id
    override def test(): Try[Unit] = Success(())
  }

  "The test runner should" - {

    "Publish a test status with state started once the test has been kicked off" in logException {

      val probe : TestProbe = TestProbe()
      system.eventStream.subscribe(probe.ref, classOf[TestStatus])

      system.actorOf(TestRunner.props(template))

      probe.fishForMessage(1.second) {
        case s : TestStatus => 
          s.id == id && s.state == TestStatus.State.Started
      }
    }
  }
}
