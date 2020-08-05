package blended.itest.runner

import akka.actor.Actor
import akka.actor.Props
import blended.util.logging.Logger
import scala.util.Try
import scala.concurrent.Future
import scala.concurrent.ExecutionContext
import akka.pattern.pipe
import scala.util.Success
import scala.util.Failure

object TestRunner {
  def props(t : TestTemplate, testId : String) : Props = Props(new TestRunner(t, testId))
}

class TestRunner(t : TestTemplate, testId : String) extends Actor {

  private val log : Logger = Logger[TestRunner]
  private implicit val eCtxt : ExecutionContext = context.system.dispatcher

  case object Start
  case class Result(result : Try[Unit])
  
  override def preStart(): Unit = {
    self ! Start
  }

  override def receive: Actor.Receive = {
    case Start => 
      val s : TestStatus = TestStatus(
        name = t.name, 
        id = testId,
        runner = Some(self),
        started = System.currentTimeMillis(),
        state = TestStatus.State.Started
      )
      log.info(s"Starting test for template [${t.name}] with id [${s.id}]")
      context.system.eventStream.publish(s)
      val f : Future[Try[Unit]] = Future{ t.test() }
      f.map(r => Result(r)).pipeTo(self)
      context.become(running(s))
  }

  private def running(s : TestStatus) : Receive = {
    case Result(Success(())) => 
      log.info(s"Test for template [${t.name}] with id [${s.id}] has succeeded.")
      finish(s.copy(state = TestStatus.State.Success))
    case Result(Failure(e)) => 
      log.info(s"Test for template [${t.name}] with id [${s.id}] has failed [${e.getMessage()}].")
      finish(s.copy(state = TestStatus.State.Failed, cause = Some(e)))
  }

  private def finish(s : TestStatus) : Unit = {
    context.system.eventStream.publish(s.copy(runner = None))
    context.stop(self)
  }
}
