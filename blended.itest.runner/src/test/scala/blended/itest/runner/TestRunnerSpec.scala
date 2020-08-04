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
import scala.util.Failure

class TestRunnerSpec extends TestKit(ActorSystem("TestRunner"))
  with LoggingFreeSpecLike
  with Matchers
  with BeforeAndAfterAll {
  
  override protected def afterAll(): Unit = {
    Await.result(system.terminate(), 10.seconds)
  }  

  private val id : String = ju.UUID.randomUUID().toString()

  private def template(f : => Try[Unit]) : TestTemplate = new TestTemplate() {

    override val name : String = "myFactory"
    override def generateId: String = id
    override def test(): Try[Unit] = f
  }

  "The test runner should" - {

    "Publish a test status with state started once the test has been kicked off" in logException {

      val probe : TestProbe = TestProbe()
      system.eventStream.subscribe(probe.ref, classOf[TestStatus])

      system.actorOf(TestRunner.props(template( Success(()) )))

      probe.fishForMessage(1.second) {
        case s : TestStatus => 
          s.id == id && s.state == TestStatus.State.Started
      }
    }

    "Publish a test status with state success once the test has been executed successfully" in logException {

      val probe : TestProbe = TestProbe()
      system.eventStream.subscribe(probe.ref, classOf[TestStatus])

      system.actorOf(TestRunner.props(template( Success(()) )))

      probe.fishForMessage(1.second) {
        case s : TestStatus => 
          s.id == id && s.state == TestStatus.State.Success
      }
    }

    "Publish a test status with state failed once the test has been executed with error" in logException {

      val probe : TestProbe = TestProbe()
      system.eventStream.subscribe(probe.ref, classOf[TestStatus])

      system.actorOf(TestRunner.props(template( Failure(new Exception("Boom")) )))

      probe.fishForMessage(1.second) {
        case s : TestStatus => 
          s.id == id && s.state == TestStatus.State.Failed && s.cause.isDefined
      }
    }
  }
}
