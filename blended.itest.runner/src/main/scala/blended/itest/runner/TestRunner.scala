package blended.itest.runner

import akka.actor.Actor
import akka.actor.Props
import blended.util.logging.Logger

object TestRunner {
  def props(t : TestTemplate) : Props = Props(new TestRunner(t))
}

class TestRunner(t : TestTemplate) extends Actor {

  private val log : Logger = Logger[TestRunner]

  case object Start
  
  override def preStart(): Unit = {
    self ! Start
  }

  override def receive: Actor.Receive = {
    case Start => 
      val s : TestStatus = TestStatus(
        name = t.name, 
        id = t.generateId,
        runner = Some(self),
        started = System.currentTimeMillis(),
        state = TestStatus.State.Started
      )
      log.info(s"Starting test for template [${t.name}] with id [${s.id}]")
      context.system.eventStream.publish(s)
      context.become(running(s))
  }

  private def running(s : TestStatus) : Receive = Actor.emptyBehavior
}
