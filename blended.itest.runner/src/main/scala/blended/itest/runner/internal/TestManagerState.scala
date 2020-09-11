package blended.itest.runner.internal

import blended.itest.runner._
import akka.actor.ActorRef
import blended.util.logging.Logger
import akka.actor.ActorSystem
import blended.jmx.{NamingStrategy, JmxObjectName, ProductMBeanManager}

class TestJmxNamingStrategy extends NamingStrategy {

  override val objectName: PartialFunction[Any,JmxObjectName] = {
    case sum : TestSummaryJMX => JmxObjectName(properties = Map(
      "component" -> "Test", "factory" -> sum.aFactoryName, "test" -> sum.aTestName
    ))
  }
}

case class TestManagerState(
  templates : List[TestTemplate] = List.empty,
  summaries : List[TestSummary] = List.empty,
  executing : Map[String, (TestTemplate, ActorRef)] = Map.empty,
  mbeanMgr : Option[ProductMBeanManager] = None
)(implicit system : ActorSystem) {

  override def toString() : String = "TestManagerState(\n  " + summaries.map(_.toString()).mkString("\n  ") + "\n)"

  private val log : Logger = Logger[TestManagerState]

  private val updateSummaries : TestSummary => List[TestSummary] = sum => sum :: summaries.filterNot(s => s.factoryName != sum.factoryName && s.testName != sum.testName)

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

    val started : TestEvent = TestEvent(
      factoryName = t.factory.name,
      testName = t.name,
      id = id,
      state = TestEvent.State.Started,
      timestamp = System.currentTimeMillis()
    )

    val sum : TestSummary = current.update(started)
    reportJmx(sum)

    copy(
      summaries = updateSummaries(sum),
      executing = Map(id -> p) ++ executing.view.filter(_._1 != id)
    )
  }

  private def reportJmx(sum : TestSummary) : Unit = {
    mbeanMgr.foreach{ mgr =>
      mgr.updateMBean(TestSummaryJMX.create(sum))
    }
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
        case _ => upd.update(s)
      }
    }

    updated match {
      case None => this
      case Some(sum) =>
        reportJmx(sum)
        val newState = copy(
          executing = if (s.state != TestEvent.State.Started) {
            executing.get(s.id).foreach(v => system.stop(v._2))
            executing.filter(_._1 != s.id)
          } else {
            executing
          },
          summaries = updateSummaries(sum)
        )
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