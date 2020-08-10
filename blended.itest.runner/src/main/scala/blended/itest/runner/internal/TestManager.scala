package blended.itest.runner.internal

import akka.actor.Actor
import blended.itest.runner.TestTemplate
import blended.itest.runner.Protocol
import blended.itest.runner.TestTemplateFactory
import blended.itest.runner.TestSummary
import blended.itest.runner.TestStatus
import blended.util.logging.Logger
import blended.itest.runner.TestRunner
import akka.actor.ActorRef
import akka.actor.Terminated

class TestManager extends Actor {

  private val log : Logger = Logger[TestManager]
  private val selector : TestSelector = new StandardTestSelector()

  case object ScheduleTest

  private case class TestManagerState(
    templates : List[TestTemplate] = List.empty,
    summaries : List[TestSummary] = List.empty,
    executing : Map[String, (TestTemplate, ActorRef)] = Map.empty,
    maxSlots : Int = 5
  ) {

    def summary(t : TestTemplate) : TestSummary = 
      summaries.find(sum => sum.factoryName == t.factory.name && sum.testName == t.name).getOrElse(TestSummary(t))

    def addTemplates(f : TestTemplateFactory) : TestManagerState = {
      copy(templates = f.templates ::: templates.filterNot(_.factory.name == f.name))
    }

    def removeTemplates(f : TestTemplateFactory) : TestManagerState = {
      copy(templates = templates.filterNot(_.factory.name == f.name))
    }

    def testStarted(id : String, t : TestTemplate, a : ActorRef) : TestManagerState = {
      val p : (TestTemplate, ActorRef) = (t,a)

      copy(
        executing = Map(id -> p) ++ executing.view.filter(_._1 != id)
      )
    }

    def testFinished(s : TestStatus) : TestManagerState = {
      val sum : Option[TestSummary] = executing.get(s.id).map(_._1) match {
        case None => 
          log.warn(s"Test [${s.id}] not found in execution map.")
          None
        case Some(templ) => 
          Some(summary(templ))
      }

      val updated : Option[TestSummary] = sum.map{ upd =>
        s.state match {
          case TestStatus.State.Started => upd
          case TestStatus.State.Failed => upd.copy(
            lastFailed = Some(s), executions = upd.executions + 1, running = upd.running - 1,
            lastExecutions = (s :: upd.lastExecutions).take(upd.maxLastExecutions)
          )
          case TestStatus.State.Success => upd.copy(
            lastSuccess = Some(s), executions = upd.executions + 1, running = upd.running -1,
            lastExecutions = (s :: upd.lastExecutions).take(upd.maxLastExecutions)
          )
        }
      }

      updated match {
        case None => this
        case Some(sum) => 
          executing.get(s.id).map(_._2).foreach(context.stop)
          copy(
            executing = executing.filter(_._1 != s.id),
            summaries = sum :: summaries.filterNot( v => v.factoryName == sum.factoryName && v.testName == sum.testName)
          )
      }
    }

    def testTerminated(a : ActorRef) : TestManagerState = {
      executing.find( _._2._2 == a) match {
        case None => this
        case Some((id, (t, ar))) => 
          val ts : TestStatus = TestStatus(
            factoryName = t.factory.name, 
            testName = t.name,
            id = id,
            runner = Some(a),
            started = System.currentTimeMillis(),
            timestamp = System.currentTimeMillis(),
            state = TestStatus.State.Failed,
            cause = Some(new Exception(s"Test [$id] for [$t] terminated enexpectedly."))
          )  

          testFinished(ts)
      }
    }
  }

  override def preStart(): Unit = {
    super.preStart()
    context.system.eventStream.subscribe(self, classOf[TestStatus])
    context.become(running(TestManagerState()))
  }

  override def receive: Actor.Receive = Actor.emptyBehavior

  private def running(state : TestManagerState) : Receive = {

    case Protocol.AddTestTemplateFactory(fact : TestTemplateFactory) => 
      context.become(running(state.addTemplates(fact)))

    case Protocol.RemoveTestTemplateFactory(fact : TestTemplateFactory) => 
      context.become(running(state.removeTemplates(fact)))

    case Protocol.GetTestTemplates => 
      sender() ! Protocol.TestTemplates(state.templates)

    case ScheduleTest => 
      if (state.executing.size < state.maxSlots) {
        selector.selectTest(state.templates, state.summaries) match {
          case None => 
          case Some(t) => 
            val id : String = t.generateId
            log.info(s"Scheduling test [$id] for [$t]")
            val actor : ActorRef = context.actorOf(TestRunner.props(t, id))
            context.watch(actor)
            context.become(running(state.testStarted(id, t, actor)))
        }
      } 

    case Terminated(a) => context.become(running(state.testTerminated(a)))  
      
    case s : TestStatus => context.become(running(state.testFinished(s)))
  }
}
