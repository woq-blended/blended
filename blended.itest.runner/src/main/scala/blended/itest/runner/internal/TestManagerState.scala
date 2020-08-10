package blended.itest.runner.internal

import blended.itest.runner._
import akka.actor.ActorRef
import blended.util.logging.Logger
import akka.actor.PoisonPill

case class TestManagerState(
  templates : List[TestTemplate] = List.empty,
  summaries : List[TestSummary] = List.empty,
  executing : Map[String, (TestTemplate, ActorRef)] = Map.empty
) {

  override def toString() : String = "TestManagerState(\n  " + summaries.map(_.toString()).mkString("\n  ") + "\n)"

  private val log : Logger = Logger[TestManagerState]

  def summary(t : TestTemplate) : TestSummary = 
    summaries.find(sum => sum.factoryName == t.factory.name && sum.testName == t.name).getOrElse(TestSummary(t))

  def addTemplates(f : TestTemplateFactory) : TestManagerState = {
    copy(templates = f.templates ::: templates.filterNot(_.factory.name == f.name))
  }

  def removeTemplates(f : TestTemplateFactory) : TestManagerState = {
    copy(templates = templates.filterNot(_.factory.name == f.name))
  }

  def testStarted(id : String, t : TestTemplate, a : ActorRef) : TestManagerState = {

    log.info(s"Test run [$id] for [${t.factory.name}::${t.name}] started.")

    val p : (TestTemplate, ActorRef) = (t,a)

    val current : TestSummary = summary(t)

    val sum : TestSummary = current.copy(
      lastStarted = Some(System.currentTimeMillis()),
      running = current.running + 1
    )

    copy(
      summaries = sum :: summaries.filterNot(s => s.factoryName == t.factory.name && s.testName == t.name),
      executing = Map(id -> p) ++ executing.view.filter(_._1 != id)
    )
  }

  def testFinished(s : TestEvent) : TestManagerState = {

    val sum : Option[TestSummary] = executing.get(s.id).map(_._1) match {
      case None => 
        log.warn(s"Test [${s.id}] not found in execution map.")
        None
      case Some(templ) => 
        Some(summary(templ))
    }

    val updated : Option[TestSummary] = sum.map{ upd =>
      s.state match {
        case TestEvent.State.Started => upd
        case TestEvent.State.Failed => 
          log.info(s"Test execution [${s.id}] for [${s.factoryName}::${s.testName}] has failed.")
          upd.copy(
            lastFailed = Some(s), executions = upd.executions + 1, running = upd.running - 1,
            lastExecutions = (s :: upd.lastExecutions).take(upd.maxLastExecutions)
          )
        case TestEvent.State.Success => 
          log.info(s"Test execution [${s.id}] for [${s.factoryName}::${s.testName}] has succeeded.")
          upd.copy(
            lastSuccess = Some(s), executions = upd.executions + 1, running = upd.running -1,
            lastExecutions = (s :: upd.lastExecutions).take(upd.maxLastExecutions)
          )
      }
    }

    updated match {
      case None => this
      case Some(sum) => 
        val newState = copy(
          executing = if (s.state != TestEvent.State.Started) {
            executing.get(s.id).foreach(_._2 ! PoisonPill)
            executing.filter(_._1 != s.id)
          } else {
            executing
          },
          summaries = sum :: summaries.filterNot( v => v.factoryName == sum.factoryName && v.testName == sum.testName)
        )
        log.info(newState.toString())
        newState
    }
  }

  def testTerminated(a : ActorRef) : TestManagerState = {
    executing.find( _._2._2 == a) match {
      case None => this
      case Some((id, (t, ar))) => 
        val ts : TestEvent = TestEvent(
          factoryName = t.factory.name, 
          testName = t.name,
          id = id,
          timestamp = System.currentTimeMillis(),
          state = TestEvent.State.Failed,
          cause = Some(new Exception(s"Test [$id] for [$t] terminated unexpectedly."))
        )  

        testFinished(ts)
    }
  }
}