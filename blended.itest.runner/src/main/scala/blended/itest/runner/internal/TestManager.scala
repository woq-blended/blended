package blended.itest.runner.internal

import akka.actor.Actor
import blended.itest.runner._
import blended.util.logging.Logger
import akka.actor.ActorRef
import akka.actor.Terminated
import akka.actor.Props
import blended.jmx.OpenMBeanExporter

object TestManager {
  def props(slots : Int, exporter : OpenMBeanExporter) : Props = Props(new TestManager(slots, exporter))
}

class TestManager(maxSlots : Int, exporter : OpenMBeanExporter) extends Actor {

  private val log : Logger = Logger[TestManager]
  private val selector : TestSelector = new StandardTestSelector()

  case object ScheduleTest

  override def preStart(): Unit = {
    super.preStart()
    context.system.eventStream.subscribe(self, classOf[TestEvent])
    context.become(running(TestManagerState(exporter = Some(exporter))(context.system)))
  }

  override def receive: Actor.Receive = Actor.emptyBehavior

  private def scheduleTest() : Unit = self ! ScheduleTest

  private def running(state : TestManagerState) : Receive = {

    case Protocol.AddTestTemplateFactory(fact : TestTemplateFactory) => 
      val newState : TestManagerState = state.addTemplates(fact)
      log.info(s"Added template factory [${fact.name}], [${newState.templates.size}] templates in total")
      context.become(running(newState))
      scheduleTest()

    case Protocol.RemoveTestTemplateFactory(fact : TestTemplateFactory) => 
      val newState : TestManagerState = state.removeTemplates(fact)
      log.info(s"Removed template factory [${fact.name}], [${newState.templates.size}] templates in total")
      context.become(running(newState))
      scheduleTest()

    case Protocol.GetTestTemplates => 
      sender() ! Protocol.TestTemplates(state.templates)

    case ScheduleTest => 
      if (state.executing.size < maxSlots) {
        selector.selectTest(state.templates, state.summaries) match {
          case None => 
          case Some(t) => 
            val id : String = t.generateId
            val actor : ActorRef = context.actorOf(TestRunner.props(t, id))
            context.watch(actor)
            context.become(running(state.testStarted(id, t, actor)))
        }
      } 

    case Terminated(a) => 
      context.become(running(state.testTerminated(a)))  
      
    case evt : TestEvent => 
      context.become(running(state.testFinished(evt)))
      scheduleTest()
  }
}
