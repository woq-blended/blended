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
      val s : TestEvent = TestEvent(
        factoryName = t.factory.name,
        testName = t.name,
        id = testId,
        state = TestEvent.State.Started
      )
      log.info(s"Starting test for template [${t.factory.name}::${t.name}] with id [${s.id}]")
      context.system.eventStream.publish(s)
      val f : Future[Try[Unit]] = Future{ t.test() }
      f.map(r => Result(r)).pipeTo(self)
      context.become(running(s))
  }

  private def running(s : TestEvent) : Receive = {
    case Result(Success(())) =>
      log.info(s"Test for template [${t.factory.name}::${t.name}] with id [${s.id}] has succeeded.")
      finish(s.copy(state = TestEvent.State.Success))
    case Result(Failure(e)) =>
      log.info(s"Test for template [${t.factory.name}::${t.name}] with id [${s.id}] has failed [${e.getMessage()}].")
      finish(s.copy(state = TestEvent.State.Failed, cause = Some(e)))
  }

  private def finish(s : TestEvent) : Unit = {
    context.system.eventStream.publish(s)
  }
}
